package pl.flipbot.negotiation;

import pl.flipbot.negotiation.NegotiationPolicyDefaults.CounterRuleValue;
import pl.flipbot.negotiation.NegotiationPolicyDefaults.ResolvedStepPolicy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Entity-facing operations shared by the main and additional product editors.
 *
 * This class deliberately does not change target validation, message normalization,
 * active-negotiation locking, or strategy versioning. Those rules differ by editor.
 */
public final class NegotiationStepPolicySupport {

    private NegotiationStepPolicySupport() {
    }

    public static List<NegotiationStep> orderedSteps(List<NegotiationStep> steps) {
        return steps.stream()
                .sorted(Comparator.comparing(step ->
                        step.getStepNumber() == null
                                ? Integer.MAX_VALUE
                                : step.getStepNumber()))
                .toList();
    }

    public static boolean samePolicy(
            NegotiationStep existing,
            ResolvedStepPolicy requested
    ) {
        if (existing.getRejectionAction() != requested.rejectionAction()
                || !Objects.equals(existing.getRejectionWaitHours(), requested.rejectionWaitHours())
                || existing.getCounterOfferDefaultAction() != requested.counterDefaultAction()
                || !Objects.equals(existing.getCounterOfferDefaultWaitHours(), requested.counterDefaultWaitHours())) {
            return false;
        }

        List<CounterRuleValue> left = existing.getCounterOfferRules().stream()
                .map(rule -> new CounterRuleValue(
                        rule.getMinimumDiscountPercent(), rule.getAction(), rule.getWaitHours()))
                .sorted(Comparator.comparing(CounterRuleValue::minimumDiscountPercent))
                .toList();
        List<CounterRuleValue> right = requested.rules().stream()
                .sorted(Comparator.comparing(CounterRuleValue::minimumDiscountPercent))
                .toList();

        if (left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            CounterRuleValue a = left.get(i);
            CounterRuleValue b = right.get(i);
            if (!sameDecimal(a.minimumDiscountPercent(), b.minimumDiscountPercent())
                    || a.action() != b.action()
                    || !Objects.equals(a.waitHours(), b.waitHours())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Mutate the existing JPA element collection in place. Replacing the list
     * object could break Hibernate dirty checking for managed negotiation steps.
     */
    public static void applyPolicy(
            NegotiationStep step,
            ResolvedStepPolicy policy
    ) {
        step.setRejectionAction(policy.rejectionAction());
        step.setRejectionWaitHours(policy.rejectionWaitHours());
        step.setCounterOfferDefaultAction(policy.counterDefaultAction());
        step.setCounterOfferDefaultWaitHours(policy.counterDefaultWaitHours());
        step.getCounterOfferRules().clear();
        step.getCounterOfferRules().addAll(toRuleEntities(policy.rules()));
    }

    public static List<SellerCounterOfferRule> toRuleEntities(List<CounterRuleValue> rules) {
        return rules.stream()
                .sorted(Comparator.comparing(CounterRuleValue::minimumDiscountPercent))
                .map(rule -> SellerCounterOfferRule.builder()
                        .minimumDiscountPercent(rule.minimumDiscountPercent())
                        .action(rule.action())
                        .waitHours(rule.waitHours())
                        .build())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private static boolean sameDecimal(BigDecimal left, BigDecimal right) {
        return left == null || right == null ? left == right : left.compareTo(right) == 0;
    }
}
