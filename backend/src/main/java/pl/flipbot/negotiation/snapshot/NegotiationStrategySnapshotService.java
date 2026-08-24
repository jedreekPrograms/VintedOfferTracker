package pl.flipbot.negotiation.snapshot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.flipbot.bot.configuration.BotConfiguration;
import pl.flipbot.listing.Listing;
import pl.flipbot.listing.ListingRepository;
import pl.flipbot.listing.ListingStatus;
import pl.flipbot.negotiation.NegotiationStep;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NegotiationStrategySnapshotService {

    static final int CURRENT_SCHEMA_VERSION = 1;

    private final ObjectMapper objectMapper;
    private final ListingRepository listingRepository;

    public void captureForNewNegotiationIfMissing(Listing listing) {
        captureIfMissing(listing, NegotiationPricingMode.DECREASING_CONCESSIONS);
    }

    public void captureLegacyIfMissing(Listing listing) {
        captureIfMissing(listing, NegotiationPricingMode.LEGACY_RATIO);
    }

    public void ensureLegacySnapshots(List<Listing> listings) {
        if (listings == null) {
            return;
        }
        for (Listing listing : listings) {
            captureLegacyIfMissing(listing);
        }
    }

    /**
     * Returns the immutable number of steps reserved by an active conversation.
     * A malformed or missing snapshot fails safe by returning the supplied live
     * ladder size, so budget capacity can never be increased by a parse error.
     */
    public int stepCountForActiveListing(Listing listing, int liveFallbackStepCount) {
        int safeFallback = Math.max(liveFallbackStepCount, 0);
        if (listing == null
                || listing.getNegotiationStrategySnapshot() == null
                || listing.getNegotiationStrategySnapshot().isBlank()) {
            return safeFallback;
        }

        try {
            NegotiationStrategySnapshot snapshot = objectMapper.readValue(
                    listing.getNegotiationStrategySnapshot(),
                    NegotiationStrategySnapshot.class
            );

            if (snapshot.schemaVersion() != CURRENT_SCHEMA_VERSION
                    || snapshot.steps() == null
                    || snapshot.steps().isEmpty()) {
                log.warn(
                        "[NEGOTIATION STRATEGY] Listing {} has an unusable strategy snapshot for quota reservation. Falling back to live step count {}.",
                        listing.getId(),
                        safeFallback
                );
                return safeFallback;
            }

            return snapshot.steps().size();
        } catch (Exception exception) {
            log.error(
                    "[NEGOTIATION STRATEGY] Could not parse strategy snapshot for active listing {} while calculating reserved quota. Failing safe with live step count {}.",
                    listing.getId(),
                    safeFallback,
                    exception
            );
            return safeFallback;
        }
    }

    @Transactional
    public int backfillExistingActiveNegotiations() {
        List<Listing> active = new ArrayList<>();
        active.addAll(listingRepository.findByStatusOrderByIdAsc(ListingStatus.NEGOTIATING));
        active.addAll(listingRepository.findByStatusOrderByIdAsc(ListingStatus.ACTION_REQUIRED));

        int captured = 0;
        for (Listing listing : active) {
            if (hasSnapshot(listing)) {
                continue;
            }
            captureLegacyIfMissing(listing);
            captured++;
        }

        if (captured > 0) {
            log.warn(
                    "[NEGOTIATION STRATEGY] Backfilled {} existing active negotiation(s) with LEGACY_RATIO snapshots before live strategy edits are allowed.",
                    captured
            );
        } else {
            log.info("[NEGOTIATION STRATEGY] No legacy active negotiation snapshots required backfill.");
        }
        return captured;
    }

    NegotiationStrategySnapshot buildSnapshot(
            BotConfiguration configuration,
            NegotiationPricingMode pricingMode
    ) {
        if (configuration == null) {
            throw new IllegalStateException("Cannot snapshot negotiation strategy without bot configuration.");
        }

        List<NegotiationStrategySnapshot.Step> steps = configuration.getNegotiationSteps()
                .stream()
                .sorted(Comparator.comparing(
                        step -> step.getStepNumber() == null
                                ? Integer.MAX_VALUE
                                : step.getStepNumber()
                ))
                .map(this::toSnapshotStep)
                .toList();

        if (steps.isEmpty()) {
            throw new IllegalStateException("Cannot snapshot a negotiation strategy with no steps.");
        }

        return new NegotiationStrategySnapshot(
                CURRENT_SCHEMA_VERSION,
                pricingMode,
                Boolean.TRUE.equals(configuration.getAutoRaiseOfferToVintedMinimum()),
                configuration.getMaxAutomaticOffer(),
                steps
        );
    }

    private void captureIfMissing(
            Listing listing,
            NegotiationPricingMode pricingMode
    ) {
        if (listing == null || hasSnapshot(listing)) {
            return;
        }
        if (listing.getBot() == null || listing.getBot().getConfiguration() == null) {
            throw new IllegalStateException(
                    "Cannot snapshot negotiation strategy for listing without bot configuration."
            );
        }

        NegotiationStrategySnapshot snapshot = buildSnapshot(
                listing.getBot().getConfiguration(),
                pricingMode
        );

        try {
            listing.setNegotiationStrategySnapshot(
                    objectMapper.writeValueAsString(snapshot)
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Could not serialize negotiation strategy snapshot for listing "
                            + listing.getId(),
                    exception
            );
        }

        log.info(
                "[NEGOTIATION STRATEGY] Listing {} / marketplace {} captured {} strategy snapshot with {} step(s).",
                listing.getId(),
                listing.getListingId(),
                pricingMode,
                snapshot.steps().size()
        );
    }

    private NegotiationStrategySnapshot.Step toSnapshotStep(NegotiationStep step) {
        List<NegotiationStrategySnapshot.CounterOfferRule> rules =
                step.getCounterOfferRules().stream()
                        .map(rule -> new NegotiationStrategySnapshot.CounterOfferRule(
                                rule.getMinimumDiscountPercent(),
                                rule.getAction(),
                                rule.getWaitHours()
                        ))
                        .toList();

        return new NegotiationStrategySnapshot.Step(
                step.getStepNumber(),
                step.getOfferPrice(),
                step.getMaxAcceptedCounterOffer(),
                step.getMessage(),
                step.getRejectionAction(),
                step.getRejectionWaitHours(),
                step.getCounterOfferDefaultAction(),
                step.getCounterOfferDefaultWaitHours(),
                rules
        );
    }

    private boolean hasSnapshot(Listing listing) {
        return listing.getNegotiationStrategySnapshot() != null
                && !listing.getNegotiationStrategySnapshot().isBlank();
    }
}
