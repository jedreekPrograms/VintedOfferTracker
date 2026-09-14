package pl.flipbot.listing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pl.flipbot.bot.configuration.BotAdditionalTarget;
import pl.flipbot.listing.dto.CreateListingRequest;
import pl.flipbot.negotiation.audit.RealActionAuditOutcome;
import pl.flipbot.negotiation.audit.RealActionAuditRepository;
import pl.flipbot.negotiation.guard.RealActionType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ListingRediscoveryService {

    private static final ZoneId DISCOVERY_ZONE =
            ZoneId.of("Europe/Warsaw");

    private static final Set<ListingStatus> DAILY_RETRYABLE_STATUSES =
            EnumSet.of(
                    ListingStatus.UNAVAILABLE,
                    ListingStatus.SKIPPED_OFFER_TOO_LOW,
                    ListingStatus.SKIPPED_OUTSIDE_PRICE_RANGE,
                    ListingStatus.SKIPPED_CANNOT_NEGOTIATE,
                    ListingStatus.SKIPPED_TARGET_MISMATCH
            );

    /**
     * A listing owned by a disabled additional product may be moved to a
     * currently scanned product only while it is still pre-negotiation state.
     * Anything that represents a real conversation or a deliberate user/
     * terminal decision remains permanently attached to the original product.
     */
    private static final Set<ListingStatus> INACTIVE_TARGET_REASSIGNABLE_STATUSES =
            EnumSet.of(
                    ListingStatus.DISCOVERED,
                    ListingStatus.UNAVAILABLE,
                    ListingStatus.SKIPPED_OFFER_TOO_LOW,
                    ListingStatus.SKIPPED_OUTSIDE_PRICE_RANGE,
                    ListingStatus.SKIPPED_CANNOT_NEGOTIATE,
                    ListingStatus.SKIPPED_TARGET_MISMATCH
            );

    private final ListingRepository listingRepository;
    private final RealActionAuditRepository realActionAuditRepository;

    /**
     * Cheap pre-check used on the already loaded discovery snapshot. The locked
     * transaction below repeats every safety check before changing anything.
     */
    public boolean shouldAttemptRequalification(Listing listing) {
        return shouldAttemptRequalification(
                listing,
                LocalDate.now(DISCOVERY_ZONE)
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Listing> requalifyIfEligible(
            Long botId,
            String marketplaceListingId,
            CreateListingRequest freshListing
    ) {
        Listing listing = listingRepository
                .findByBotIdAndListingIdForUpdate(
                        botId,
                        marketplaceListingId
                )
                .orElse(null);

        if (listing == null) {
            return Optional.empty();
        }

        LocalDateTime now = LocalDateTime.now(DISCOVERY_ZONE);

        if (!shouldAttemptRequalification(listing, now.toLocalDate())) {
            return Optional.empty();
        }

        if (hasAnyFirstOfferAudit(listing)) {
            log.debug(
                    "[DAILY REDISCOVERY] Listing backendId={} marketplaceId={} for bot {} will not be requalified because a FIRST_OFFER audit already exists.",
                    listing.getId(),
                    listing.getListingId(),
                    botId
            );
            return Optional.empty();
        }

        ListingStatus previousStatus = listing.getStatus();
        refreshAsDiscovered(listing, freshListing, now);

        Listing saved = listingRepository.saveAndFlush(listing);

        log.info(
                "[DAILY REDISCOVERY] Requalified listing backendId={} marketplaceId={} for bot {} from {} to DISCOVERED after it reappeared in today's fresh scan. Fresh price={}. It cannot be requalified again until the next Europe/Warsaw day.",
                saved.getId(),
                saved.getListingId(),
                botId,
                previousStatus,
                saved.getOriginalPrice()
        );

        return Optional.of(saved);
    }

    /**
     * Reuses the existing backend Listing row instead of creating a duplicate
     * when a product is disabled and later added again as a new target. The
     * reassignment is intentionally fail-closed: only untouched/retryable rows
     * from an INACTIVE additional target can move, and any FIRST_OFFER audit
     * (confirmed or ambiguous) permanently prevents reassignment.
     *
     * @param replacementTarget null means the main product; non-null means the
     *                          currently scanned additional product.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Listing> reassignFromInactiveTargetIfEligible(
            Long botId,
            String marketplaceListingId,
            BotAdditionalTarget replacementTarget,
            CreateListingRequest freshListing
    ) {
        Listing listing = listingRepository
                .findByBotIdAndListingIdForUpdate(
                        botId,
                        marketplaceListingId
                )
                .orElse(null);

        if (!isSafeForInactiveTargetReassignment(listing)) {
            return Optional.empty();
        }

        if (hasAnyFirstOfferAudit(listing)) {
            log.warn(
                    "[PRODUCT REASSIGN] Refusing to move backend listing {} / marketplace listing {} for bot {} because a FIRST_OFFER audit already exists.",
                    listing.getId(),
                    listing.getListingId(),
                    botId
            );
            return Optional.empty();
        }

        BotAdditionalTarget previousTarget = listing.getAdditionalTarget();
        Long previousTargetId = previousTarget.getId();
        Long replacementTargetId = replacementTarget == null
                ? null
                : replacementTarget.getId();
        ListingStatus previousStatus = listing.getStatus();

        listing.setAdditionalTarget(replacementTarget);
        LocalDateTime now = LocalDateTime.now(DISCOVERY_ZONE);
        refreshAsDiscovered(listing, freshListing, now);

        Listing saved = listingRepository.saveAndFlush(listing);

        log.info(
                "[PRODUCT REASSIGN] Reassigned backend listing {} / marketplace listing {} for bot {} from disabled additional target {} to {}. Previous status={}, fresh price={}.",
                saved.getId(),
                saved.getListingId(),
                botId,
                previousTargetId,
                replacementTargetId == null ? "MAIN" : "ADDITIONAL:" + replacementTargetId,
                previousStatus,
                saved.getOriginalPrice()
        );

        return Optional.of(saved);
    }

    boolean shouldAttemptRequalification(
            Listing listing,
            LocalDate today
    ) {
        if (listing == null
                || listing.getStatus() == null
                || !DAILY_RETRYABLE_STATUSES.contains(listing.getStatus())) {
            return false;
        }

        Integer currentStep = listing.getCurrentStep();
        if (currentStep != null && currentStep != 0) {
            return false;
        }

        if (hasText(listing.getConversationId())
                || hasText(listing.getConversationUrl())) {
            return false;
        }

        LocalDateTime lastFreshDiscoveryAt = listing.getLastFreshDiscoveryAt();
        return lastFreshDiscoveryAt == null
                || lastFreshDiscoveryAt.toLocalDate().isBefore(today);
    }

    boolean isSafeForInactiveTargetReassignment(Listing listing) {
        if (listing == null
                || listing.getStatus() == null
                || !INACTIVE_TARGET_REASSIGNABLE_STATUSES.contains(listing.getStatus())) {
            return false;
        }

        BotAdditionalTarget previousTarget = listing.getAdditionalTarget();
        if (previousTarget == null || Boolean.TRUE.equals(previousTarget.getActive())) {
            return false;
        }

        Integer currentStep = listing.getCurrentStep();
        if (currentStep != null && currentStep != 0) {
            return false;
        }

        if (Boolean.TRUE.equals(listing.getAwaitingSellerResponse())
                || hasText(listing.getConversationId())
                || hasText(listing.getConversationUrl())) {
            return false;
        }

        return true;
    }

    private boolean hasAnyFirstOfferAudit(Listing listing) {
        if (listing == null || listing.getId() == null) {
            return true;
        }

        return realActionAuditRepository
                .existsByBackendListingIdAndActionTypeAndOutcome(
                        listing.getId(),
                        RealActionType.FIRST_OFFER,
                        RealActionAuditOutcome.CONFIRMED
                )
                || realActionAuditRepository
                .existsByBackendListingIdAndActionTypeAndOutcome(
                        listing.getId(),
                        RealActionType.FIRST_OFFER,
                        RealActionAuditOutcome.AMBIGUOUS
                );
    }

    private void refreshAsDiscovered(
            Listing listing,
            CreateListingRequest freshListing,
            LocalDateTime now
    ) {
        listing.setTitle(freshListing.getTitle());
        listing.setUrl(freshListing.getUrl());
        listing.setOriginalPrice(freshListing.getOriginalPrice());
        listing.setCurrentPrice(freshListing.getOriginalPrice());

        listing.setCurrentStep(0);
        listing.setAwaitingSellerResponse(false);
        listing.setConversationId(null);
        listing.setConversationUrl(null);
        listing.setStatus(ListingStatus.DISCOVERED);
        listing.setDecisionAt(null);

        listing.setCurrentStepStartedAt(null);
        listing.setSellerActivityAt(null);
        listing.setReadDetectedAt(null);
        listing.setFormalResponseFingerprint(null);
        listing.setFormalResponseDetectedAt(null);
        listing.setLastFreshDiscoveryAt(now);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
