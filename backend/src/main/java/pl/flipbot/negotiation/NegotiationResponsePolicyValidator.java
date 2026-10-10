package pl.flipbot.negotiation;

import pl.flipbot.negotiation.NegotiationPolicyDefaults.CounterRuleValue;
import pl.flipbot.negotiation.NegotiationPolicyDefaults.ResolvedStepPolicy;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

/**
 * Single owner for counteroffer threshold/action/wait invariants used by both
 * product editors. The locale-specific messages remain byte-compatible with
 * the previous public API. No persistence or strategy update occurs here.
 */
public final class NegotiationResponsePolicyValidator {
    private static final int MAX_RESPONSE_WAIT_HOURS = 720;
    private static final BigDecimal MAX_DISCOUNT_PERCENT = new BigDecimal("100");

    public enum MessageStyle { MAIN, ADDITIONAL }

    private NegotiationResponsePolicyValidator() {
    }

    public static void validate(ResolvedStepPolicy policy, int stepNumber, MessageStyle style) {
        validateReaction(policy.rejectionAction(), policy.rejectionWaitHours(),
                style == MessageStyle.MAIN
                        ? "Step " + stepNumber + " rejection policy"
                        : "Krok " + stepNumber + " po odrzuceniu",
                style);
        validateReaction(policy.counterDefaultAction(), policy.counterDefaultWaitHours(),
                style == MessageStyle.MAIN
                        ? "Step " + stepNumber + " counteroffer fallback"
                        : "Krok " + stepNumber + " domyślna kontroferta",
                style);
        Set<String> thresholds = new HashSet<>();
        for (CounterRuleValue rule : policy.rules()) {
            BigDecimal discount = rule.minimumDiscountPercent();
            if (discount == null || discount.signum() <= 0
                    || discount.compareTo(MAX_DISCOUNT_PERCENT) > 0) {
                throw new IllegalArgumentException(style == MessageStyle.MAIN
                        ? "Step " + stepNumber
                          + " counteroffer discount threshold must be greater than 0 and at most 100%."
                        : "Próg procentowy kroku " + stepNumber
                          + " musi być większy od 0 i nie większy niż 100%.");
            }
            String normalized = discount.stripTrailingZeros().toPlainString();
            if (!thresholds.add(normalized)) {
                throw new IllegalArgumentException(style == MessageStyle.MAIN
                        ? "Step " + stepNumber + " contains duplicate counteroffer discount threshold "
                          + normalized + "%."
                        : "Krok " + stepNumber + " zawiera powtórzony próg "
                          + normalized + "%.");
            }
            validateReaction(rule.action(), rule.waitHours(),
                    style == MessageStyle.MAIN
                            ? "Step " + stepNumber + " counteroffer rule " + normalized + "%"
                            : "Krok " + stepNumber + " próg " + normalized + "%",
                    style);
        }
    }

    private static void validateReaction(NegotiationReactionAction action, Integer waitHours,
                                         String label, MessageStyle style) {
        if (action == null) {
            throw new IllegalArgumentException(label +
                    (style == MessageStyle.MAIN ? " has no action." : " nie ma ustawionej akcji."));
        }
        if (action == NegotiationReactionAction.WAIT_BEFORE_NEXT_STEP
                && (waitHours == null || waitHours < 1 || waitHours > MAX_RESPONSE_WAIT_HOURS)) {
            throw new IllegalArgumentException(label +
                    (style == MessageStyle.MAIN
                            ? " wait time must be between 1 and 720 hours."
                            : " wymaga czasu 1-720 godzin."));
        }
    }
}
