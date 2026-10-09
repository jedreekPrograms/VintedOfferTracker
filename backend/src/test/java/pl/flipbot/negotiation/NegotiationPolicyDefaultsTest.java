package pl.flipbot.negotiation;

import org.junit.jupiter.api.Test;
import pl.flipbot.negotiation.NegotiationPolicyDefaults.CounterRuleValue;
import pl.flipbot.negotiation.NegotiationPolicyDefaults.ResolvedStepPolicy;
import pl.flipbot.negotiation.dto.CreateNegotiationStepRequest;
import pl.flipbot.negotiation.dto.SellerCounterOfferRuleRequest;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static pl.flipbot.negotiation.NegotiationPolicyDefaults.resolvePolicy;

class NegotiationPolicyDefaultsTest {

    @Test
    void missingPolicyRetainsLegacyStepSpecificWaits() {
        CreateNegotiationStepRequest request = new CreateNegotiationStepRequest();

        ResolvedStepPolicy first = resolvePolicy(request, 1);
        assertEquals(NegotiationReactionAction.NEXT_STEP_NOW, first.rejectionAction());
        assertNull(first.rejectionWaitHours());

        for (int step = 2; step <= 5; step++) {
            ResolvedStepPolicy resolved = resolvePolicy(request, step);
            assertEquals(NegotiationReactionAction.WAIT_BEFORE_NEXT_STEP,
                    resolved.rejectionAction());
            assertEquals(step == 2 ? 6 : step == 3 ? 12 : 24,
                    resolved.rejectionWaitHours());
        }
    }

    @Test
    void omittedRulesGetDefaultsButExplicitEmptyRulesRemainEmpty() {
        CreateNegotiationStepRequest request = new CreateNegotiationStepRequest();
        ResolvedStepPolicy defaults = resolvePolicy(request, 1);

        assertEquals(NegotiationReactionAction.WAIT_BEFORE_NEXT_STEP,
                defaults.counterDefaultAction());
        assertEquals(6, defaults.counterDefaultWaitHours());
        assertEquals(List.of(
                new CounterRuleValue(new BigDecimal("10"),
                        NegotiationReactionAction.WAIT_BEFORE_NEXT_STEP, 2),
                new CounterRuleValue(new BigDecimal("15"),
                        NegotiationReactionAction.NEXT_STEP_NOW, null)
        ), defaults.rules());

        request.setCounterOfferRules(List.of());
        assertTrue(resolvePolicy(request, 1).rules().isEmpty());
    }

    @Test
    void immediateActionsDropObsoleteWaitsAndNullCustomRulesAreIgnored() {
        CreateNegotiationStepRequest request = new CreateNegotiationStepRequest();
        request.setRejectionAction(NegotiationReactionAction.NEXT_STEP_NOW);
        request.setRejectionWaitHours(48);
        request.setCounterOfferDefaultAction(NegotiationReactionAction.NEXT_STEP_NOW);
        request.setCounterOfferDefaultWaitHours(72);

        SellerCounterOfferRuleRequest rule = new SellerCounterOfferRuleRequest();
        rule.setMinimumDiscountPercent(new BigDecimal("12.5"));
        rule.setAction(NegotiationReactionAction.NEXT_STEP_NOW);
        rule.setWaitHours(9);
        request.setCounterOfferRules(Arrays.asList(null, rule));

        ResolvedStepPolicy resolved = resolvePolicy(request, 3);
        assertNull(resolved.rejectionWaitHours());
        assertNull(resolved.counterDefaultWaitHours());
        assertEquals(List.of(new CounterRuleValue(
                new BigDecimal("12.5"),
                NegotiationReactionAction.NEXT_STEP_NOW,
                null
        )), resolved.rules());
    }

    @Test
    void configuredWaitingPolicyIsPreserved() {
        CreateNegotiationStepRequest request = new CreateNegotiationStepRequest();
        request.setRejectionAction(NegotiationReactionAction.WAIT_BEFORE_NEXT_STEP);
        request.setRejectionWaitHours(48);
        request.setCounterOfferDefaultAction(
                NegotiationReactionAction.WAIT_BEFORE_NEXT_STEP);
        request.setCounterOfferDefaultWaitHours(12);

        ResolvedStepPolicy resolved = resolvePolicy(request, 2);
        assertEquals(48, resolved.rejectionWaitHours());
        assertEquals(12, resolved.counterDefaultWaitHours());
    }
}
