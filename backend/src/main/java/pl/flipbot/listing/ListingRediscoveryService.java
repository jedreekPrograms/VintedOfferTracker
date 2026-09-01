package pl.flipbot.listing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
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

        boolean hasConfirmedFirstOffer = realActionAuditRepository
                .existsByBackendListingIdAndActionTypeAndOutcome(
                        listing.getId(),
                        RealActionType.FIRST_OFFER,
                        RealActionAuditOutcome.CONFIRMED
                );

        if (hasConfirmedFirstOffer) {
            log.debug(
                    "[DAILY REDISCOVERY] Listing backendId={} marketplaceId={} for bot {} will not be requalified because a confirmed FIRST_OFFER exists.",
                    listing.getId(),
                    listing.getListingId(),
                    botId
            );
            return Optional.empty();
        }

        ListingStatus previousStatus = listing.getStatus();

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

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
