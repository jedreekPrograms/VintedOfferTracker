package pl.flipbot.negotiation;

import pl.flipbot.negotiation.dto.CreateNegotiationStepRequest;
import pl.flipbot.negotiation.dto.SellerCounterOfferRuleRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Shared backward-compatible defaults for both main and additional product negotiation steps.
 * Input validation and entity ownership remain in their respective services.
 *
 * null counterOfferRules means the client omitted the rules and gets defaults;
 * an empty list explicitly disables the rules.
 */
public final class NegotiationPolicyDefaults {

    private NegotiationPolicyDefaults() {
    }

    public static ResolvedStepPolicy resolvePolicy(
            CreateNegotiationStepRequest request,
            int stepNumber
    ) {
        NegotiationReactionAction rejectionAction = request.getRejectionAction();
        Integer rejectionWait = request.getRejectionWaitHours();
        if (rejectionAction == null) {
            if (stepNumber == 1) {
                rejectionAction = NegotiationReactionAction.NEXT_STEP_NOW;
                rejectionWait = null;
            } else {
                rejectionAction = NegotiationReactionAction.WAIT_BEFORE_NEXT_STEP;
                rejectionWait = defaultRejectionWaitHours(stepNumber);
            }
        }
        if (rejectionAction == NegotiationReactionAction.NEXT_STEP_NOW) {
            rejectionWait = null;
        }

        NegotiationReactionAction counterDefault = request.getCounterOfferDefaultAction();
        Integer counterWait = request.getCounterOfferDefaultWaitHours();
        if (counterDefault == null) {
            counterDefault = NegotiationReactionAction.WAIT_BEFORE_NEXT_STEP;
            counterWait = 6;
        }
        if (counterDefault == NegotiationReactionAction.NEXT_STEP_NOW) {
            counterWait = null;
        }

        List<CounterRuleValue> rules = request.getCounterOfferRules() == null
                ? defaultCounterOfferRules()
                : request.getCounterOfferRules().stream()
                        .filter(Objects::nonNull)
                        .map(NegotiationPolicyDefaults::toRuleValue)
                        .toList();

        return new ResolvedStepPolicy(
                rejectionAction, rejectionWait, counterDefault, counterWait, rules
        );
    }

    private static CounterRuleValue toRuleValue(SellerCounterOfferRuleRequest request) {
        NegotiationReactionAction action = request.getAction();
        Integer wait = request.getWaitHours();
        if (action == NegotiationReactionAction.NEXT_STEP_NOW) {
            wait = null;
        }
        return new CounterRuleValue(request.getMinimumDiscountPercent(), action, wait);
    }

    private static int defaultRejectionWaitHours(int stepNumber) {
        if (stepNumber == 2) {
            return 6;
        }
        if (stepNumber == 3) {
            return 12;
        }
        return 24;
    }

    private static List<CounterRuleValue> defaultCounterOfferRules() {
        return List.of(
                new CounterRuleValue(
                        new BigDecimal("10"),
                        NegotiationReactionAction.WAIT_BEFORE_NEXT_STEP,
                        2
                ),
                new CounterRuleValue(
                        new BigDecimal("15"),
                        NegotiationReactionAction.NEXT_STEP_NOW,
                        null
                )
        );
    }

    public record ResolvedStepPolicy(
            NegotiationReactionAction rejectionAction,
            Integer rejectionWaitHours,
            NegotiationReactionAction counterDefaultAction,
            Integer counterDefaultWaitHours,
            List<CounterRuleValue> rules
    ) {
    }

    public record CounterRuleValue(
            BigDecimal minimumDiscountPercent,
            NegotiationReactionAction action,
            Integer waitHours
    ) {
    }
}
