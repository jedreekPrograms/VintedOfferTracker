package pl.flipbot.negotiation.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.flipbot.listing.Listing;
import pl.flipbot.listing.ListingRepository;
import pl.flipbot.listing.ListingStatus;
import pl.flipbot.negotiation.NegotiationStep;
import pl.flipbot.negotiation.audit.dto.RealActionAuditResponse;
import pl.flipbot.negotiation.audit.dto.UpsertRealActionAuditRequest;
import pl.flipbot.negotiation.guard.RealActionGuard;
import pl.flipbot.negotiation.guard.RealActionGuardRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class RealActionAuditService {

    private final ListingRepository listingRepository;
    private final RealActionGuardRepository realActionGuardRepository;
    private final RealActionAuditRepository realActionAuditRepository;

    @Transactional
    public RealActionAuditResponse record(
            Long botId,
            Long listingId,
            UpsertRealActionAuditRequest request
    ) {
        Listing listing = listingRepository.findByIdAndBotIdForUpdate(
                        listingId,
                        botId
                )
                .orElseThrow(
                        () -> new NoSuchElementException(
                                "Listing " + listingId + " was not found for bot " + botId
                        )
                );

        return recordWithLockedListing(
                botId,
                listing,
                request
        );
    }

    @Transactional
    public void backfillAmbiguousFromBlockedGuard(
            Listing listing,
            RealActionGuard guard,
            String reason
    ) {
        Objects.requireNonNull(listing, "Listing cannot be null");
        Objects.requireNonNull(guard, "Real action guard cannot be null");

        if (realActionAuditRepository.findByRequestId(guard.getRequestId()).isPresent()) {
            return;
        }

        UpsertRealActionAuditRequest request =
                new UpsertRealActionAuditRequest(
                        guard.getRequestId(),
                        guard.getActionType(),
                        guard.getStepNumber(),
                        null,
                        RealActionAuditOutcome.AMBIGUOUS,
                        RealActionMessageStatus.UNKNOWN,
                        reason
                );

        recordWithLockedListing(
                listing.getBot().getId(),
                listing,
                request
        );

        log.warn(
                "[REAL ACTION AUDIT] Backfilled AMBIGUOUS audit from unresolved guard. requestId={}, bot={}, listing={}, action={}, step={}",
                guard.getRequestId(),
                listing.getBot().getId(),
                listing.getId(),
                guard.getActionType(),
                guard.getStepNumber()
        );
    }

    @Transactional(readOnly = true)
    public List<RealActionAuditResponse> getForBot(
            Long botId
    ) {
        return realActionAuditRepository
                .findByBotIdOrderByCreatedAtDesc(botId)
                .stream()
                .map(this::map)
                .toList();
    }

    private RealActionAuditResponse recordWithLockedListing(
            Long botId,
            Listing listing,
            UpsertRealActionAuditRequest request
    ) {
        RealActionAudit existing = realActionAuditRepository
                .findByRequestId(request.requestId())
                .orElse(null);

        if (existing != null) {
            validateReplay(existing, botId, listing, request);
            merge(existing, listing, request);
            RealActionAudit saved = realActionAuditRepository.saveAndFlush(existing);

            log.info(
                    "[REAL ACTION AUDIT] Updated replay requestId={} bot={} listing={} action={} step={} outcome={} messageStatus={}",
                    saved.getRequestId(),
                    saved.getBotId(),
                    saved.getBackendListingId(),
                    saved.getActionType(),
                    saved.getStepNumber(),
                    saved.getOutcome(),
                    saved.getMessageStatus()
            );

            return map(saved);
        }

        RealActionGuard guard = realActionGuardRepository
                .findByRequestId(request.requestId())
                .orElseThrow(
                        () -> new IllegalStateException(
                                "Cannot create real-action audit without the matching persistent guard. requestId="
                                        + request.requestId()
                        )
                );

        validateGuard(guard, listing, request);

        if (request.outcome() == RealActionAuditOutcome.CONFIRMED) {
            validateConfirmedByListingState(listing, request);
        }

        BigDecimal auditPrice = resolveAuditPrice(
                listing,
                request
        );

        LocalDateTime now = LocalDateTime.now();

        RealActionAudit audit = RealActionAudit.builder()
                .requestId(request.requestId())
                .botId(botId)
                .backendListingId(listing.getId())
                .marketplaceListingId(listing.getListingId())
                .conversationId(normalizeNullable(listing.getConversationId()))
                .actionType(request.actionType())
                .stepNumber(request.stepNumber())
                .offerPrice(auditPrice)
                .outcome(request.outcome())
                .messageStatus(request.messageStatus())
                .failureReason(normalizeFailureReason(request.failureReason()))
                .createdAt(now)
                .updatedAt(now)
                .build();

        RealActionAudit saved = realActionAuditRepository.saveAndFlush(audit);

        log.warn(
                "[REAL ACTION AUDIT] Recorded requestId={} bot={} listing={} marketplaceListing={} action={} step={} price={} outcome={} messageStatus={}",
                saved.getRequestId(),
                saved.getBotId(),
                saved.getBackendListingId(),
                saved.getMarketplaceListingId(),
                saved.getActionType(),
                saved.getStepNumber(),
                saved.getOfferPrice(),
                saved.getOutcome(),
                saved.getMessageStatus()
        );

        return map(saved);
    }

    private void validateGuard(
            RealActionGuard guard,
            Listing listing,
            UpsertRealActionAuditRequest request
    ) {
        if (!Objects.equals(guard.getListing().getId(), listing.getId())
                || guard.getActionType() != request.actionType()
                || !Objects.equals(guard.getStepNumber(), request.stepNumber())) {
            throw new IllegalStateException(
                    "Real-action audit request does not match its persistent guard. requestId="
                            + request.requestId()
            );
        }
    }

    private void validateReplay(
            RealActionAudit audit,
            Long botId,
            Listing listing,
            UpsertRealActionAuditRequest request
    ) {
        if (!Objects.equals(audit.getBotId(), botId)
                || !Objects.equals(audit.getBackendListingId(), listing.getId())
                || audit.getActionType() != request.actionType()
                || !Objects.equals(audit.getStepNumber(), request.stepNumber())) {
            throw new IllegalStateException(
                    "Real-action audit requestId "
                            + request.requestId()
                            + " is already associated with another action"
            );
        }
    }

    private void merge(
            RealActionAudit audit,
            Listing listing,
            UpsertRealActionAuditRequest request
    ) {
        if (audit.getOutcome() == RealActionAuditOutcome.AMBIGUOUS
                && request.outcome() == RealActionAuditOutcome.CONFIRMED) {
            validateConfirmedByListingState(listing, request);
            audit.setOutcome(RealActionAuditOutcome.CONFIRMED);
            audit.setOfferPrice(resolveConfirmedPrice(listing));
            audit.setFailureReason(null);
        }

        audit.setMessageStatus(
                mergeMessageStatus(
                        audit.getMessageStatus(),
                        request.messageStatus()
                )
        );

        String currentConversationId = normalizeNullable(listing.getConversationId());
        if (currentConversationId != null) {
            if (audit.getConversationId() != null
                    && !audit.getConversationId().equals(currentConversationId)) {
                throw new IllegalStateException(
                        "Listing conversation id changed for real-action audit requestId "
                                + request.requestId()
                );
            }
            audit.setConversationId(currentConversationId);
        }

        if (audit.getOutcome() == RealActionAuditOutcome.AMBIGUOUS
                && audit.getFailureReason() == null) {
            audit.setFailureReason(
                    normalizeFailureReason(request.failureReason())
            );
        }

        audit.setUpdatedAt(LocalDateTime.now());
    }

    private BigDecimal resolveAuditPrice(
            Listing listing,
            UpsertRealActionAuditRequest request
    ) {
        if (request.outcome() == RealActionAuditOutcome.CONFIRMED) {
            return resolveConfirmedPrice(listing);
        }

        if (request.offerPrice() != null) {
            return requirePositivePrice(
                    request.offerPrice(),
                    "Audit offer price"
            );
        }

        return resolveConfiguredStepPrice(
                listing,
                request.stepNumber()
        );
    }

    private BigDecimal resolveConfirmedPrice(
            Listing listing
    ) {
        return requirePositivePrice(
                listing.getCurrentPrice(),
                "Confirmed listing current price"
        );
    }

    private BigDecimal resolveConfiguredStepPrice(
            Listing listing,
            Integer stepNumber
    ) {
        if (listing.getBot() == null
                || listing.getBot().getConfiguration() == null
                || listing.getBot().getConfiguration().getNegotiationSteps() == null) {
            throw new IllegalStateException(
                    "Cannot derive audit price because bot negotiation steps are missing"
            );
        }

        return listing.getBot()
                .getConfiguration()
                .getNegotiationSteps()
                .stream()
                .filter(Objects::nonNull)
                .filter(step -> Objects.equals(
                        stepNumber,
                        step.getStepNumber()
                ))
                .map(NegotiationStep::getOfferPrice)
                .filter(Objects::nonNull)
                .map(price -> requirePositivePrice(
                        price,
                        "Configured negotiation step price"
                ))
                .findFirst()
                .orElseThrow(
                        () -> new IllegalStateException(
                                "Cannot derive audit price for negotiation step "
                                        + stepNumber
                        )
                );
    }

    private BigDecimal requirePositivePrice(
            BigDecimal value,
            String label
    ) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalStateException(
                    label + " must be positive"
            );
        }

        return value;
    }

    private RealActionMessageStatus mergeMessageStatus(
            RealActionMessageStatus current,
            RealActionMessageStatus requested
    ) {
        if (current == RealActionMessageStatus.CONFIRMED
                || requested == RealActionMessageStatus.CONFIRMED) {
            return RealActionMessageStatus.CONFIRMED;
        }

        if (current == RealActionMessageStatus.FAILED
                || requested == RealActionMessageStatus.FAILED) {
            return RealActionMessageStatus.FAILED;
        }

        return RealActionMessageStatus.UNKNOWN;
    }

    private void validateConfirmedByListingState(
            Listing listing,
            UpsertRealActionAuditRequest request
    ) {
        if (listing.getStatus() != ListingStatus.NEGOTIATING) {
            throw new IllegalStateException(
                    "Confirmed real-action audit requires NEGOTIATING listing. Current status: "
                            + listing.getStatus()
            );
        }

        Integer currentStep = listing.getCurrentStep();
        if (currentStep == null || currentStep < request.stepNumber()) {
            throw new IllegalStateException(
                    "Confirmed real-action audit requires listing currentStep >= audited step. Current="
                            + currentStep
                            + ", audited="
                            + request.stepNumber()
            );
        }
    }

    private String normalizeFailureReason(String value) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            return null;
        }

        return normalized.length() <= 1000
                ? normalized
                : normalized.substring(0, 1000);
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private RealActionAuditResponse map(
            RealActionAudit audit
    ) {
        return new RealActionAuditResponse(
                audit.getId(),
                audit.getRequestId(),
                audit.getBotId(),
                audit.getBackendListingId(),
                audit.getMarketplaceListingId(),
                audit.getConversationId(),
                audit.getActionType(),
                audit.getStepNumber(),
                audit.getOfferPrice(),
                audit.getOutcome(),
                audit.getMessageStatus(),
                audit.getFailureReason(),
                audit.getCreatedAt(),
                audit.getUpdatedAt()
        );
    }
}
