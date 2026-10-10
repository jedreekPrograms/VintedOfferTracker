package pl.flipbot.negotiation;

import org.junit.jupiter.api.Test;
import pl.flipbot.negotiation.NegotiationPolicyDefaults.CounterRuleValue;
import pl.flipbot.negotiation.NegotiationPolicyDefaults.ResolvedStepPolicy;
import pl.flipbot.negotiation.NegotiationResponsePolicyValidator.MessageStyle;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NegotiationResponsePolicyValidatorTest {

    @Test
    void acceptsWaitBoundaryAndNormalizesEquivalentDecimalThresholds() {
        ResolvedStepPolicy policy = policy(
                NegotiationReactionAction.WAIT_BEFORE_NEXT_STEP, 720,
                NegotiationReactionAction.WAIT_BEFORE_NEXT_STEP, 1,
                List.of(rule("10.00", NegotiationReactionAction.NEXT_STEP_NOW, null))
        );
        assertDoesNotThrow(() -> NegotiationResponsePolicyValidator.validate(policy, 3, MessageStyle.MAIN));
        assertDoesNotThrow(() -> NegotiationResponsePolicyValidator.validate(policy, 3, MessageStyle.ADDITIONAL));
    }

    @Test
    void rejectsNumericDuplicateDiscountsWithEditorSpecificMessages() {
        ResolvedStepPolicy policy = policy(NegotiationReactionAction.NEXT_STEP_NOW, null,
                NegotiationReactionAction.NEXT_STEP_NOW, null,
                List.of(rule("10.0", NegotiationReactionAction.NEXT_STEP_NOW, null),
                        rule("10.00", NegotiationReactionAction.NEXT_STEP_NOW, null)));
        assertEquals("Step 2 contains duplicate counteroffer discount threshold 10%.",
                failure(policy, 2, MessageStyle.MAIN));
        assertEquals("Krok 2 zawiera powtórzony próg 10%.",
                failure(policy, 2, MessageStyle.ADDITIONAL));
    }

    @Test
    void preservesThresholdBoundsAndMessagesInBothEditors() {
        for (String invalid : new String[]{"0", "-1", "100.01"}) {
            ResolvedStepPolicy p = policy(NegotiationReactionAction.NEXT_STEP_NOW, null,
                    NegotiationReactionAction.NEXT_STEP_NOW, null,
                    List.of(rule(invalid, NegotiationReactionAction.NEXT_STEP_NOW, null)));
            assertEquals("Step 4 counteroffer discount threshold must be greater than 0 and at most 100%.",
                    failure(p, 4, MessageStyle.MAIN));
            assertEquals("Próg procentowy kroku 4 musi być większy od 0 i nie większy niż 100%.",
                    failure(p, 4, MessageStyle.ADDITIONAL));
        }
    }

    @Test
    void preservesMissingActionAndOutOfRangeWaitMessages() {
        ResolvedStepPolicy missing = policy(null, null,
                NegotiationReactionAction.NEXT_STEP_NOW, null, List.of());
        assertEquals("Step 1 rejection policy has no action.",
                failure(missing, 1, MessageStyle.MAIN));
        assertEquals("Krok 1 po odrzuceniu nie ma ustawionej akcji.",
                failure(missing, 1, MessageStyle.ADDITIONAL));

        ResolvedStepPolicy late = policy(NegotiationReactionAction.NEXT_STEP_NOW, null,
                NegotiationReactionAction.WAIT_BEFORE_NEXT_STEP, 721, List.of());
        assertEquals("Step 1 counteroffer fallback wait time must be between 1 and 720 hours.",
                failure(late, 1, MessageStyle.MAIN));
        assertEquals("Krok 1 domyślna kontroferta wymaga czasu 1-720 godzin.",
                failure(late, 1, MessageStyle.ADDITIONAL));
    }

    @Test
    void validatesRulesNotOnlyTopLevelReactions() {
        ResolvedStepPolicy policy = policy(NegotiationReactionAction.NEXT_STEP_NOW, null,
                NegotiationReactionAction.NEXT_STEP_NOW, null,
                List.of(rule("15", NegotiationReactionAction.WAIT_BEFORE_NEXT_STEP, null)));
        assertEquals("Step 5 counteroffer rule 15% wait time must be between 1 and 720 hours.",
                failure(policy, 5, MessageStyle.MAIN));
        assertEquals("Krok 5 próg 15% wymaga czasu 1-720 godzin.",
                failure(policy, 5, MessageStyle.ADDITIONAL));
    }

    private static String failure(ResolvedStepPolicy policy, int step, MessageStyle style) {
        return assertThrows(IllegalArgumentException.class,
                () -> NegotiationResponsePolicyValidator.validate(policy, step, style)).getMessage();
    }

    private static ResolvedStepPolicy policy(
            NegotiationReactionAction rejection, Integer rejectionHours,
            NegotiationReactionAction fallback, Integer fallbackHours,
            List<CounterRuleValue> rules
    ) {
        return new ResolvedStepPolicy(rejection, rejectionHours, fallback, fallbackHours, rules);
    }

    private static CounterRuleValue rule(
            String discount, NegotiationReactionAction action, Integer hours
    ) {
        return new CounterRuleValue(new BigDecimal(discount), action, hours);
    }
}
