package pl.flipbot.playwright.negotiation;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.model.BotConfigurationDto;
import pl.flipbot.playwright.model.NegotiationStepDto;
import pl.flipbot.playwright.model.SellerCounterOfferRuleDto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
final class NegotiationStrategyResolver {

    static final String LEGACY_RATIO = "LEGACY_RATIO";
    static final String DECREASING_CONCESSIONS = "DECREASING_CONCESSIONS";

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    BotConfigurationDto resolve(
            ListingResponseDto listing,
            BotConfigurationDto liveConfiguration
    ) {
        if (listing == null) {
            throw new IllegalArgumentException("Listing cannot be null");
        }
        if (liveConfiguration == null) {
            throw new IllegalArgumentException("Live bot configuration cannot be null");
        }

        String rawSnapshot = listing.negotiationStrategySnapshot();
        if (rawSnapshot == null || rawSnapshot.isBlank()) {
            /*
             * Compatibility path for tests/older backend responses. Production
             * V31 startup backfill should make this unreachable for active DB
             * conversations. Do not silently opt an old conversation into the
             * new pricing algorithm.
             */
            BotConfigurationDto legacy = copyLiveConfiguration(liveConfiguration);
            legacy.setNegotiationPricingMode(LEGACY_RATIO);
            log.warn(
                    "[NEGOTIATION STRATEGY] Active listing {} has no persisted strategy snapshot. Falling back to the live definition with LEGACY_RATIO semantics for compatibility.",
                    listing.listingId()
            );
            return legacy;
        }

        NegotiationStrategySnapshotDto snapshot;
        try {
            snapshot = objectMapper.readValue(
                    rawSnapshot,
                    NegotiationStrategySnapshotDto.class
            );
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Could not parse negotiation strategy snapshot for marketplace listing "
                            + listing.listingId(),
                    exception
            );
        }

        validateSnapshot(snapshot, listing);

        BotConfigurationDto resolved = copyLiveConfiguration(liveConfiguration);
        resolved.setAutoRaiseOfferToVintedMinimum(
                Boolean.TRUE.equals(snapshot.autoRaiseOfferToVintedMinimum())
        );
        resolved.setMaxAutomaticOffer(
                effectiveSafetyCap(
                        snapshot.maxAutomaticOffer(),
                        liveConfiguration.getMaxAutomaticOffer()
                )
        );
        resolved.setNegotiationSteps(toStepDtos(snapshot.steps()));
        resolved.setNegotiationPricingMode(snapshot.pricingMode());

        log.debug(
                "[NEGOTIATION STRATEGY] Listing {} uses frozen {} snapshot. steps={}, snapshotCap={}, liveSafetyCap={}, effectiveCap={}.",
                listing.listingId(),
                snapshot.pricingMode(),
                snapshot.steps().size(),
                snapshot.maxAutomaticOffer(),
                liveConfiguration.getMaxAutomaticOffer(),
                resolved.getMaxAutomaticOffer()
        );

        return resolved;
    }

    private BotConfigurationDto copyLiveConfiguration(BotConfigurationDto source) {
        BotConfigurationDto copy = new BotConfigurationDto();
        copy.setMarketplace(source.getMarketplace());
        copy.setCategoryPath(source.getCategoryPath() == null
                ? null
                : List.copyOf(source.getCategoryPath()));
        copy.setBrand(source.getBrand());
        copy.setTargetMode(source.getTargetMode());
        copy.setModel(source.getModel());
        copy.setSearchQuery(source.getSearchQuery());
        copy.setMinPrice(source.getMinPrice());
        copy.setMaxPrice(source.getMaxPrice());
        copy.setAutoRaiseOfferToVintedMinimum(source.getAutoRaiseOfferToVintedMinimum());
        copy.setMaxAutomaticOffer(source.getMaxAutomaticOffer());
        copy.setDailyNegotiationBudget(source.getDailyNegotiationBudget());
        copy.setNegotiationSteps(source.getNegotiationSteps());
        copy.setNegotiationPricingMode(source.getNegotiationPricingMode());
        return copy;
    }

    private List<NegotiationStepDto> toStepDtos(
            List<NegotiationStrategySnapshotDto.Step> snapshotSteps
    ) {
        return snapshotSteps.stream()
                .sorted(Comparator.comparing(NegotiationStrategySnapshotDto.Step::stepNumber))
                .map(step -> {
                    NegotiationStepDto dto = new NegotiationStepDto();
                    dto.setStepNumber(step.stepNumber());
                    dto.setOfferPrice(step.offerPrice());
                    dto.setMaxAcceptedCounterOffer(step.maxAcceptedCounterOffer());
                    dto.setMessage(step.message());
                    dto.setRejectionAction(step.rejectionAction());
                    dto.setRejectionWaitHours(step.rejectionWaitHours());
                    dto.setCounterOfferDefaultAction(step.counterOfferDefaultAction());
                    dto.setCounterOfferDefaultWaitHours(step.counterOfferDefaultWaitHours());

                    List<SellerCounterOfferRuleDto> rules = new ArrayList<>();
                    if (step.counterOfferRules() != null) {
                        for (NegotiationStrategySnapshotDto.CounterOfferRule rule
                                : step.counterOfferRules()) {
                            SellerCounterOfferRuleDto ruleDto = new SellerCounterOfferRuleDto();
                            ruleDto.setMinimumDiscountPercent(rule.minimumDiscountPercent());
                            ruleDto.setAction(rule.action());
                            ruleDto.setWaitHours(rule.waitHours());
                            rules.add(ruleDto);
                        }
                    }
                    dto.setCounterOfferRules(rules);
                    return dto;
                })
                .toList();
    }

    private BigDecimal effectiveSafetyCap(
            BigDecimal snapshotCap,
            BigDecimal liveCap
    ) {
        if (snapshotCap == null) {
            return liveCap;
        }
        if (liveCap == null) {
            return snapshotCap;
        }
        return snapshotCap.min(liveCap);
    }

    private void validateSnapshot(
            NegotiationStrategySnapshotDto snapshot,
            ListingResponseDto listing
    ) {
        if (snapshot == null
                || snapshot.schemaVersion() == null
                || snapshot.schemaVersion() != 1) {
            throw new IllegalStateException(
                    "Unsupported negotiation strategy snapshot version for listing "
                            + listing.listingId()
            );
        }
        if (!LEGACY_RATIO.equals(snapshot.pricingMode())
                && !DECREASING_CONCESSIONS.equals(snapshot.pricingMode())) {
            throw new IllegalStateException(
                    "Unsupported negotiation pricing mode '"
                            + snapshot.pricingMode()
                            + "' for listing "
                            + listing.listingId()
            );
        }
        if (snapshot.steps() == null || snapshot.steps().isEmpty()) {
            throw new IllegalStateException(
                    "Negotiation strategy snapshot has no steps for listing "
                            + listing.listingId()
            );
        }
        for (NegotiationStrategySnapshotDto.Step step : snapshot.steps()) {
            if (step == null
                    || step.stepNumber() == null
                    || step.stepNumber() <= 0
                    || step.offerPrice() == null
                    || step.offerPrice().signum() <= 0) {
                throw new IllegalStateException(
                        "Negotiation strategy snapshot contains an invalid step for listing "
                                + listing.listingId()
                );
            }
        }
    }
}
