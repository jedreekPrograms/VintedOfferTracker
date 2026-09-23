package pl.flipbot.negotiation.guard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.flipbot.listing.Listing;
import pl.flipbot.listing.ListingRepository;
import pl.flipbot.listing.ListingStatus;
import pl.flipbot.negotiation.audit.RealActionAuditService;
import pl.flipbot.negotiation.guard.dto.AcquireRealActionGuardRequest;
import pl.flipbot.negotiation.guard.dto.RealActionGuardResponse;
import pl.flipbot.negotiation.guard.dto.ReleaseRealActionGuardRequest;
import pl.flipbot.negotiation.guard.dto.ReleaseRealActionGuardResponse;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RealActionGuardService {

    private final ListingRepository listingRepository;
    private final RealActionGuardRepository realActionGuardRepository;
    private final RealActionAuditService realActionAuditService;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public RealActionGuardResponse acquire(
            Long botId,
            Long listingId,
            AcquireRealActionGuardRequest request
    ) {
        Listing listing = lockListing(botId, listingId);

        RealActionGuard requestReplay =
                realActionGuardRepository.findByRequestId(request.requestId())
                        .orElse(null);

        if (requestReplay != null) {
            validateReplay(requestReplay, listing, request);

            if (isConfirmedByListingState(requestReplay, listing)) {
                log.warn(
                        "[REAL ACTION GUARD] Replay request {} refers to an action already confirmed by listing state. "
                                + "Persisting/reconciling audit before removing stale guard. Bot={}, listing={}, action={}, step={}, status={}, currentStep={}",
                        request.requestId(),
                        botId,
                        listingId,
                        requestReplay.getActionType(),
                        requestReplay.getStepNumber(),
                        listing.getStatus(),
                        listing.getCurrentStep()
                );

                realActionAuditService.backfillConfirmedFromStaleGuard(
                        listing,
                        requestReplay
                );

                confirmMarketplaceClaimIfFirstOffer(listing, requestReplay);

                realActionGuardRepository.delete(requestReplay);
                realActionGuardRepository.flush();

                return new RealActionGuardResponse(
                        false,
                        true,
                        null,
                        requestReplay.getActionType(),
                        requestReplay.getStepNumber(),
                        requestReplay.getCreatedAt()
                );
            }

            validateActionAgainstListing(listing, request);

            log.info(
                    "[REAL ACTION GUARD] Replayed acquisition for bot {}, listing {}, action {}, step {}, requestId={}",
                    botId,
                    listingId,
                    request.actionType(),
                    request.stepNumber(),
                    request.requestId()
            );

            return toResponse(requestReplay, true, true);
        }

        RealActionGuard activeGuard =
                realActionGuardRepository.findByListing_Id(listingId)
                        .orElse(null);

        if (activeGuard != null
                && isConfirmedByListingState(activeGuard, listing)) {

            log.warn(
                    "[REAL ACTION GUARD] Found confirmed stale guard for bot {}, listing {}, action {}, step {}, requestId={}. "
                            + "Persisting/reconciling audit before cleanup. Listing status={}, currentStep={}",
                    botId,
                    listingId,
                    activeGuard.getActionType(),
                    activeGuard.getStepNumber(),
                    activeGuard.getRequestId(),
                    listing.getStatus(),
                    listing.getCurrentStep()
            );

            realActionAuditService.backfillConfirmedFromStaleGuard(
                    listing,
                    activeGuard
            );

            confirmMarketplaceClaimIfFirstOffer(listing, activeGuard);

            realActionGuardRepository.delete(activeGuard);
            realActionGuardRepository.flush();
            activeGuard = null;
        }

        if (activeGuard != null) {
            realActionAuditService.backfillAmbiguousFromBlockedGuard(
                    listing,
                    activeGuard,
                    "A later acquire found the persistent guard still unresolved and listing state did not confirm delivery."
            );

            log.warn(
                    "[REAL ACTION GUARD] Acquisition blocked for bot {}, listing {}. Existing action={}, step={}, createdAt={}",
                    botId,
                    listingId,
                    activeGuard.getActionType(),
                    activeGuard.getStepNumber(),
                    activeGuard.getCreatedAt()
            );

            return new RealActionGuardResponse(
                    false,
                    false,
                    null,
                    activeGuard.getActionType(),
                    activeGuard.getStepNumber(),
                    activeGuard.getCreatedAt()
            );
        }

        validateActionAgainstListing(listing, request);

        if (request.actionType() == RealActionType.FIRST_OFFER) {
            MarketplaceClaimResult claimResult =
                    acquireMarketplaceNegotiationClaim(listing, request.requestId());

            if (claimResult != MarketplaceClaimResult.ACQUIRED) {
                if (claimResult == MarketplaceClaimResult.BLOCKED_CONFIRMED) {
                    markAsAlreadyNegotiated(listing);

                    log.warn(
                            "[MARKETPLACE CLAIM] FIRST_OFFER permanently blocked for bot {}, backend listing {}, marketplace listing {}. "
                                    + "Another bot already owns a confirmed negotiation for this marketplace listing.",
                            botId,
                            listingId,
                            listing.getListingId()
                    );
                } else {
                    log.warn(
                            "[MARKETPLACE CLAIM] FIRST_OFFER temporarily blocked for bot {}, backend listing {}, marketplace listing {}. "
                                    + "Another bot currently owns the pre-submit reservation. Listing remains DISCOVERED and may retry if that reservation is safely released.",
                            botId,
                            listingId,
                            listing.getListingId()
                    );
                }

                return blockedResponse(RealActionType.FIRST_OFFER, 1);
            }
        }

        if (request.actionType() == RealActionType.NEXT_STEP
                && !ownsMarketplaceNegotiation(listing, request.requestId())) {

            markAsAlreadyNegotiated(listing);

            log.error(
                    "[MARKETPLACE CLAIM] NEXT_STEP blocked for bot {}, backend listing {}, marketplace listing {}. "
                            + "Another bot owns this marketplace negotiation, so this duplicate negotiation is now terminal and no further real action is allowed.",
                    botId,
                    listingId,
                    listing.getListingId()
            );

            return blockedResponse(
                    RealActionType.NEXT_STEP,
                    request.stepNumber()
            );
        }

        RealActionGuard guard =
                RealActionGuard.builder()
                        .listing(listing)
                        .requestId(request.requestId())
                        .actionType(request.actionType())
                        .stepNumber(request.stepNumber())
                        .createdAt(LocalDateTime.now())
                        .build();

        RealActionGuard saved = realActionGuardRepository.saveAndFlush(guard);

        log.warn(
                "[REAL ACTION GUARD] ACQUIRED for bot {}, listing {}, action {}, step {}, requestId={}",
                botId,
                listingId,
                saved.getActionType(),
                saved.getStepNumber(),
                saved.getRequestId()
        );

        return toResponse(saved, true, false);
    }

    @Transactional
    public ReleaseRealActionGuardResponse release(
            Long botId,
            Long listingId,
            ReleaseRealActionGuardRequest request
    ) {
        Listing listing = lockListing(botId, listingId);

        RealActionGuard guard =
                realActionGuardRepository.findByListing_Id(listingId)
                        .orElse(null);

        if (guard == null) {
            return new ReleaseRealActionGuardResponse(
                    false,
                    true
            );
        }

        if (!Objects.equals(guard.getRequestId(), request.requestId())) {
            throw new IllegalStateException(
                    "Real action guard for listing "
                            + listingId
                            + " belongs to a different requestId"
            );
        }

        if (guard.getActionType() == RealActionType.FIRST_OFFER) {
            if (firstOfferWasConfirmed(listing)) {
                confirmMarketplaceNegotiationClaim(
                        listing,
                        guard.getRequestId()
                );
            } else {
                releasePreSubmitMarketplaceClaim(
                        listing,
                        guard.getRequestId()
                );
            }
        }

        realActionGuardRepository.delete(guard);
        realActionGuardRepository.flush();

        log.info(
                "[REAL ACTION GUARD] RELEASED for bot {}, listing {}, action {}, step {}, requestId={}",
                botId,
                listingId,
                guard.getActionType(),
                guard.getStepNumber(),
                guard.getRequestId()
        );

        return new ReleaseRealActionGuardResponse(
                true,
                false
        );
    }

    private MarketplaceClaimResult acquireMarketplaceNegotiationClaim(
            Listing listing,
            UUID requestId
    ) {
        String marketplace = resolveMarketplace(listing);

        int inserted = jdbcTemplate.update(
                """
                INSERT INTO marketplace_negotiation_claim (
                    marketplace,
                    marketplace_listing_id,
                    owner_bot_id,
                    owner_listing_id,
                    request_id,
                    claimed_at,
                    confirmed_at
                )
                VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, NULL)
                ON CONFLICT (marketplace, marketplace_listing_id) DO NOTHING
                """,
                marketplace,
                listing.getListingId(),
                listing.getBot().getId(),
                listing.getId(),
                requestId
        );

        if (inserted == 1) {
            log.warn(
                    "[MARKETPLACE CLAIM] ACQUIRED PRE-SUBMIT {}:{} for bot {}, backend listing {}, requestId={}",
                    marketplace,
                    listing.getListingId(),
                    listing.getBot().getId(),
                    listing.getId(),
                    requestId
            );
            return MarketplaceClaimResult.ACQUIRED;
        }

        Map<String, Object> owner = findMarketplaceOwner(
                marketplace,
                listing.getListingId()
        );

        boolean sameOwner = isSameOwner(owner, listing);
        if (sameOwner) {
            return MarketplaceClaimResult.ACQUIRED;
        }

        boolean confirmed = owner.get("confirmed_at") != null;

        log.warn(
                "[MARKETPLACE CLAIM] CONFLICT for {}:{} requested by bot {}, backend listing {}. Existing owner bot={}, backend listing={}, confirmedAt={}.",
                marketplace,
                listing.getListingId(),
                listing.getBot().getId(),
                listing.getId(),
                owner.get("owner_bot_id"),
                owner.get("owner_listing_id"),
                owner.get("confirmed_at")
        );

        return confirmed
                ? MarketplaceClaimResult.BLOCKED_CONFIRMED
                : MarketplaceClaimResult.BLOCKED_PRE_SUBMIT;
    }

    private boolean ownsMarketplaceNegotiation(
            Listing listing,
            UUID requestId
    ) {
        String marketplace = resolveMarketplace(listing);

        int inserted = jdbcTemplate.update(
                """
                INSERT INTO marketplace_negotiation_claim (
                    marketplace,
                    marketplace_listing_id,
                    owner_bot_id,
                    owner_listing_id,
                    request_id,
                    claimed_at,
                    confirmed_at
                )
                VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (marketplace, marketplace_listing_id) DO NOTHING
                """,
                marketplace,
                listing.getListingId(),
                listing.getBot().getId(),
                listing.getId(),
                requestId
        );

        if (inserted == 1) {
            log.warn(
                    "[MARKETPLACE CLAIM] Recovered missing durable ownership for existing negotiation {}:{} -> bot {}, backend listing {}.",
                    marketplace,
                    listing.getListingId(),
                    listing.getBot().getId(),
                    listing.getId()
            );
            return true;
        }

        Map<String, Object> owner = findMarketplaceOwner(
                marketplace,
                listing.getListingId()
        );

        if (!isSameOwner(owner, listing)) {
            return false;
        }

        if (owner.get("confirmed_at") == null) {
            int confirmed = jdbcTemplate.update(
                    """
                    UPDATE marketplace_negotiation_claim
                    SET confirmed_at = CURRENT_TIMESTAMP
                    WHERE marketplace = ?
                      AND marketplace_listing_id = ?
                      AND owner_bot_id = ?
                      AND owner_listing_id = ?
                      AND confirmed_at IS NULL
                    """,
                    marketplace,
                    listing.getListingId(),
                    listing.getBot().getId(),
                    listing.getId()
            );

            if (confirmed != 1) {
                throw new IllegalStateException(
                        "Could not confirm marketplace ownership for existing negotiation "
                                + marketplace
                                + ":"
                                + listing.getListingId()
                );
            }
        }

        return true;
    }

    private Map<String, Object> findMarketplaceOwner(
            String marketplace,
            String marketplaceListingId
    ) {
        return jdbcTemplate.queryForMap(
                """
                SELECT owner_bot_id,
                       owner_listing_id,
                       request_id,
                       claimed_at,
                       confirmed_at
                FROM marketplace_negotiation_claim
                WHERE marketplace = ?
                  AND marketplace_listing_id = ?
                """,
                marketplace,
                marketplaceListingId
        );
    }

    private boolean isSameOwner(
            Map<String, Object> owner,
            Listing listing
    ) {
        return Objects.equals(
                asLong(owner.get("owner_bot_id")),
                listing.getBot().getId()
        ) && Objects.equals(
                asLong(owner.get("owner_listing_id")),
                listing.getId()
        );
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null
                ? null
                : Long.valueOf(value.toString());
    }

    private void confirmMarketplaceClaimIfFirstOffer(
            Listing listing,
            RealActionGuard guard
    ) {
        if (guard.getActionType() == RealActionType.FIRST_OFFER) {
            confirmMarketplaceNegotiationClaim(
                    listing,
                    guard.getRequestId()
            );
        }
    }

    private void confirmMarketplaceNegotiationClaim(
            Listing listing,
            UUID requestId
    ) {
        String marketplace = resolveMarketplace(listing);

        int updated = jdbcTemplate.update(
                """
                UPDATE marketplace_negotiation_claim
                SET confirmed_at = COALESCE(confirmed_at, CURRENT_TIMESTAMP)
                WHERE marketplace = ?
                  AND marketplace_listing_id = ?
                  AND owner_bot_id = ?
                  AND owner_listing_id = ?
                  AND request_id = ?
                """,
                marketplace,
                listing.getListingId(),
                listing.getBot().getId(),
                listing.getId(),
                requestId
        );

        if (updated != 1) {
            throw new IllegalStateException(
                    "Could not confirm marketplace negotiation claim for "
                            + marketplace
                            + ":"
                            + listing.getListingId()
                            + ". Failing closed."
            );
        }
    }

    private void releasePreSubmitMarketplaceClaim(
            Listing listing,
            UUID requestId
    ) {
        String marketplace = resolveMarketplace(listing);

        int deleted = jdbcTemplate.update(
                """
                DELETE FROM marketplace_negotiation_claim
                WHERE marketplace = ?
                  AND marketplace_listing_id = ?
                  AND owner_bot_id = ?
                  AND owner_listing_id = ?
                  AND request_id = ?
                  AND confirmed_at IS NULL
                """,
                marketplace,
                listing.getListingId(),
                listing.getBot().getId(),
                listing.getId(),
                requestId
        );

        if (deleted != 1) {
            throw new IllegalStateException(
                    "Could not release pre-submit marketplace negotiation claim for "
                            + marketplace
                            + ":"
                            + listing.getListingId()
                            + ". Ownership changed or the claim is already confirmed. Failing closed."
            );
        }

        log.info(
                "[MARKETPLACE CLAIM] RELEASED PRE-SUBMIT {}:{} for bot {}, backend listing {}, requestId={}",
                marketplace,
                listing.getListingId(),
                listing.getBot().getId(),
                listing.getId(),
                requestId
        );
    }

    private String resolveMarketplace(Listing listing) {
        if (listing.getBot() == null
                || listing.getBot().getConfiguration() == null
                || listing.getBot().getConfiguration().getMarketplace() == null) {
            throw new IllegalStateException(
                    "Cannot protect marketplace negotiation for backend listing "
                            + listing.getId()
                            + " because marketplace configuration is missing"
            );
        }

        return listing.getBot().getConfiguration().getMarketplace().name();
    }

    private boolean firstOfferWasConfirmed(Listing listing) {
        return listing.getCurrentStep() != null
                && listing.getCurrentStep() >= 1;
    }

    private void markAsAlreadyNegotiated(Listing listing) {
        listing.setStatus(ListingStatus.SKIPPED_ALREADY_NEGOTIATED);
        listing.setDecisionAt(LocalDateTime.now());
        listingRepository.saveAndFlush(listing);
    }

    private RealActionGuardResponse blockedResponse(
            RealActionType actionType,
            Integer stepNumber
    ) {
        return new RealActionGuardResponse(
                false,
                false,
                null,
                actionType,
                stepNumber,
                LocalDateTime.now()
        );
    }

    private Listing lockListing(
            Long botId,
            Long listingId
    ) {
        return listingRepository.findByIdAndBotIdForUpdate(
                        listingId,
                        botId
                )
                .orElseThrow(
                        () -> new NoSuchElementException(
                                "Listing "
                                        + listingId
                                        + " was not found for bot "
                                        + botId
                        )
                );
    }

    private void validateReplay(
            RealActionGuard guard,
            Listing listing,
            AcquireRealActionGuardRequest request
    ) {
        if (!Objects.equals(guard.getListing().getId(), listing.getId())
                || guard.getActionType() != request.actionType()
                || !Objects.equals(guard.getStepNumber(), request.stepNumber())) {

            throw new IllegalStateException(
                    "Real action guard requestId "
                            + request.requestId()
                            + " is already associated with another action"
            );
        }
    }

    private void validateActionAgainstListing(
            Listing listing,
            AcquireRealActionGuardRequest request
    ) {
        switch (request.actionType()) {
            case FIRST_OFFER -> {
                if (listing.getStatus() != ListingStatus.DISCOVERED) {
                    throw new IllegalStateException(
                            "FIRST_OFFER guard requires DISCOVERED listing. Current status: "
                                    + listing.getStatus()
                    );
                }

                if (request.stepNumber() != 1) {
                    throw new IllegalStateException(
                            "FIRST_OFFER guard requires stepNumber=1"
                    );
                }
            }

            case NEXT_STEP -> {
                if (listing.getStatus() != ListingStatus.NEGOTIATING) {
                    throw new IllegalStateException(
                            "NEXT_STEP guard requires NEGOTIATING listing. Current status: "
                                    + listing.getStatus()
                    );
                }

                Integer currentStep = listing.getCurrentStep();

                if (currentStep == null
                        || request.stepNumber() != currentStep + 1) {

                    throw new IllegalStateException(
                            "NEXT_STEP guard requires exactly currentStep+1. Current step: "
                                    + currentStep
                                    + ", requested step: "
                                    + request.stepNumber()
                    );
                }
            }
        }
    }

    private boolean isConfirmedByListingState(
            RealActionGuard guard,
            Listing listing
    ) {
        Integer currentStep = listing.getCurrentStep();

        if (currentStep == null) {
            return false;
        }

        return switch (guard.getActionType()) {
            case FIRST_OFFER -> currentStep >= 1;
            case NEXT_STEP -> listing.getStatus() == ListingStatus.NEGOTIATING
                    && currentStep >= guard.getStepNumber();
        };
    }

    private RealActionGuardResponse toResponse(
            RealActionGuard guard,
            boolean acquired,
            boolean replayed
    ) {
        return new RealActionGuardResponse(
                acquired,
                replayed,
                guard.getRequestId(),
                guard.getActionType(),
                guard.getStepNumber(),
                guard.getCreatedAt()
        );
    }

    private enum MarketplaceClaimResult {
        ACQUIRED,
        BLOCKED_PRE_SUBMIT,
        BLOCKED_CONFIRMED
    }
}
