package pl.flipbot.playwright.negotiation;

import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.model.BotConfigurationDto;
import pl.flipbot.playwright.model.NegotiationStepDto;
import pl.flipbot.playwright.model.NegotiationStrategySnapshotDto;
import pl.flipbot.playwright.model.SellerCounterOfferRuleDto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the effective configuration for one already-started negotiation.
 *
 * Product identity and operational fields come from the current product
 * definition, but ladder/messages/response policies come from the immutable
 * listing snapshot. Raising the current global cap never expands an old
 * conversation. Lowering it remains an emergency safety brake for old adaptive
 * conversations as long as adaptive mode is still enabled for the product.
 */
@Slf4j
public final class NegotiationStrategyConfigurationResolver {

    private NegotiationStrategyConfigurationResolver() {
    }

    public static BotConfigurationDto resolve(
            BotConfigurationDto currentProductConfiguration,
            NegotiationStrategySnapshotDto snapshot
    ) {
        if (currentProductConfiguration == null) {
            throw new IllegalArgumentException(
                    "Current product configuration is required."
            );
        }

        if (snapshot == null
                || snapshot.getNegotiationSteps() == null
                || snapshot.getNegotiationSteps().isEmpty()) {
            return currentProductConfiguration;
        }

        BotConfigurationDto effective = copyBase(currentProductConfiguration);
        boolean snapshotAdaptive =
                Boolean.TRUE.equals(snapshot.getAutoRaiseOfferToVintedMinimum());

        effective.setAutoRaiseOfferToVintedMinimum(snapshotAdaptive);
        effective.setMaxAutomaticOffer(
                effectiveCap(
                        snapshotAdaptive,
                        snapshot.getMaxAutomaticOffer(),
                        currentProductConfiguration
                )
        );
        effective.setNegotiationSteps(copySteps(snapshot.getNegotiationSteps()));

        return effective;
    }

    private static BigDecimal effectiveCap(
            boolean snapshotAdaptive,
            BigDecimal snapshotCap,
            BotConfigurationDto currentProductConfiguration
    ) {
        if (!snapshotAdaptive || snapshotCap == null) {
            return snapshotCap;
        }

        if (!Boolean.TRUE.equals(
                currentProductConfiguration.getAutoRaiseOfferToVintedMinimum()
        )) {
            return snapshotCap;
        }

        BigDecimal currentCap =
                currentProductConfiguration.getMaxAutomaticOffer();

        if (currentCap == null || currentCap.compareTo(snapshotCap) >= 0) {
            return snapshotCap;
        }

        log.warn(
                "[NEGOTIATION STRATEGY] Applying lowered current safety cap {} to an older negotiation whose pinned cap was {}. The old cap is never increased automatically.",
                currentCap,
                snapshotCap
        );
        return currentCap;
    }

    private static BotConfigurationDto copyBase(BotConfigurationDto source) {
        BotConfigurationDto copy = new BotConfigurationDto();
        copy.setMarketplace(source.getMarketplace());
        copy.setCategoryPath(
                source.getCategoryPath() == null
                        ? null
                        : List.copyOf(source.getCategoryPath())
        );
        copy.setBrand(source.getBrand());
        copy.setTargetMode(source.getTargetMode());
        copy.setModel(source.getModel());
        copy.setSearchQuery(source.getSearchQuery());
        copy.setMinPrice(source.getMinPrice());
        copy.setMaxPrice(source.getMaxPrice());
        copy.setAutoRaiseOfferToVintedMinimum(
                source.getAutoRaiseOfferToVintedMinimum()
        );
        copy.setMaxAutomaticOffer(source.getMaxAutomaticOffer());
        copy.setDailyNegotiationBudget(source.getDailyNegotiationBudget());
        copy.setNegotiationSteps(copySteps(source.getNegotiationSteps()));
        return copy;
    }

    private static List<NegotiationStepDto> copySteps(
            List<NegotiationStepDto> source
    ) {
        if (source == null) {
            return new ArrayList<>();
        }

        List<NegotiationStepDto> result = new ArrayList<>(source.size());
        for (NegotiationStepDto step : source) {
            if (step == null) {
                continue;
            }

            NegotiationStepDto copy = new NegotiationStepDto();
            copy.setStepNumber(step.getStepNumber());
            copy.setOfferPrice(step.getOfferPrice());
            copy.setMaxAcceptedCounterOffer(step.getMaxAcceptedCounterOffer());
            copy.setMessage(step.getMessage());
            copy.setRejectionAction(step.getRejectionAction());
            copy.setRejectionWaitHours(step.getRejectionWaitHours());
            copy.setCounterOfferDefaultAction(
                    step.getCounterOfferDefaultAction()
            );
            copy.setCounterOfferDefaultWaitHours(
                    step.getCounterOfferDefaultWaitHours()
            );

            List<SellerCounterOfferRuleDto> rules = new ArrayList<>();
            if (step.getCounterOfferRules() != null) {
                for (SellerCounterOfferRuleDto rule : step.getCounterOfferRules()) {
                    if (rule == null) {
                        continue;
                    }
                    SellerCounterOfferRuleDto ruleCopy =
                            new SellerCounterOfferRuleDto();
                    ruleCopy.setMinimumDiscountPercent(
                            rule.getMinimumDiscountPercent()
                    );
                    ruleCopy.setAction(rule.getAction());
                    ruleCopy.setWaitHours(rule.getWaitHours());
                    rules.add(ruleCopy);
                }
            }
            copy.setCounterOfferRules(rules);
            result.add(copy);
        }
        return result;
    }
}
