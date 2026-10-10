package pl.flipbot.negotiation;

import org.junit.jupiter.api.Test;
import pl.flipbot.negotiation.NegotiationPolicyDefaults.CounterRuleValue;
import pl.flipbot.negotiation.NegotiationPolicyDefaults.ResolvedStepPolicy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import pl.flipbot.negotiation.dto.CreateNegotiationStepRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static pl.flipbot.negotiation.NegotiationStepPolicySupport.applyPolicy;
import static pl.flipbot.negotiation.NegotiationStepPolicySupport.orderedSteps;
import static pl.flipbot.negotiation.NegotiationStepPolicySupport.samePolicy;
import static pl.flipbot.negotiation.NegotiationStepPolicySupport.toRuleEntities;

class NegotiationStepPolicySupportTest {

    @Test
    void comparisonIgnoresDecimalScaleAndRuleOrder() {
        NegotiationStep step = step();
        step.getCounterOfferRules().addAll(toRuleEntities(List.of(
                rule("15.000", NegotiationReactionAction.NEXT_STEP_NOW, null),
                rule("10.0", NegotiationReactionAction.WAIT_BEFORE_NEXT_STEP, 2)
        )));

        assertTrue(samePolicy(step, policy(List.of(
                rule("10.000", NegotiationReactionAction.WAIT_BEFORE_NEXT_STEP, 2),
                rule("15", NegotiationReactionAction.NEXT_STEP_NOW, null)
        ))));
    }

    @Test
    void comparisonDetectsChangesToActionWaitAndDiscountRules() {
        NegotiationStep step = step();
        step.getCounterOfferRules().addAll(toRuleEntities(List.of(
                rule("10", NegotiationReactionAction.WAIT_BEFORE_NEXT_STEP, 2)
        )));

        assertFalse(samePolicy(step, policy(List.of())));
        assertFalse(samePolicy(step, policy(List.of(
                rule("10", NegotiationReactionAction.WAIT_BEFORE_NEXT_STEP, 3)
        ))));
        assertFalse(samePolicy(step, policy(List.of(
                rule("10", NegotiationReactionAction.NEXT_STEP_NOW, null)
        ))));
        assertFalse(samePolicy(step, policy(List.of(
                rule("15", NegotiationReactionAction.WAIT_BEFORE_NEXT_STEP, 2)
        ))));
        assertFalse(samePolicy(step, new ResolvedStepPolicy(
                NegotiationReactionAction.WAIT_BEFORE_NEXT_STEP, 3,
                NegotiationReactionAction.WAIT_BEFORE_NEXT_STEP, 6,
                List.of(rule("10", NegotiationReactionAction.WAIT_BEFORE_NEXT_STEP, 2))
        )));
    }

    @Test
    void applyingPolicyMutatesManagedRuleListInPlaceAndSortsRules() {
        NegotiationStep step = step();
        List<SellerCounterOfferRule> originalCollection = step.getCounterOfferRules();
        originalCollection.addAll(toRuleEntities(List.of(
                rule("5", NegotiationReactionAction.NEXT_STEP_NOW, null)
        )));

        applyPolicy(step, policy(List.of(
                rule("15", NegotiationReactionAction.NEXT_STEP_NOW, null),
                rule("10", NegotiationReactionAction.WAIT_BEFORE_NEXT_STEP, 2)
        )));

        assertSame(originalCollection, step.getCounterOfferRules());
        assertEquals(2, step.getCounterOfferRules().size());
        assertEquals(0, step.getCounterOfferRules().get(0)
                .getMinimumDiscountPercent().compareTo(new BigDecimal("10")));
        assertEquals(0, step.getCounterOfferRules().get(1)
                .getMinimumDiscountPercent().compareTo(new BigDecimal("15")));
        assertTrue(samePolicy(step, policy(List.of(
                rule("10", NegotiationReactionAction.WAIT_BEFORE_NEXT_STEP, 2),
                rule("15", NegotiationReactionAction.NEXT_STEP_NOW, null)
        ))));
    }

    @Test
    void stepSortingKeepsNullNumbersLastWithoutMutatingInput() {
        NegotiationStep first = NegotiationStep.builder().stepNumber(1).build();
        NegotiationStep second = NegotiationStep.builder().stepNumber(2).build();
        NegotiationStep unnumbered = NegotiationStep.builder().build();
        List<NegotiationStep> original = new ArrayList<>(
                List.of(unnumbered, second, first));

        assertEquals(List.of(first, second, unnumbered), orderedSteps(original));
        assertEquals(List.of(unnumbered, second, first), original);
    }

    private static NegotiationStep step() {
        return NegotiationStep.builder()
                .rejectionAction(NegotiationReactionAction.NEXT_STEP_NOW)
                .counterOfferDefaultAction(NegotiationReactionAction.WAIT_BEFORE_NEXT_STEP)
                .counterOfferDefaultWaitHours(6)
                .counterOfferRules(new ArrayList<>())
                .build();
    }

    private static ResolvedStepPolicy policy(List<CounterRuleValue> rules) {
        return new ResolvedStepPolicy(
                NegotiationReactionAction.NEXT_STEP_NOW, null,
                NegotiationReactionAction.WAIT_BEFORE_NEXT_STEP, 6, rules
        );
    }

    private static CounterRuleValue rule(
            String threshold, NegotiationReactionAction action, Integer hours
    ) {
        return new CounterRuleValue(new BigDecimal(threshold), action, hours);
    }
    @Test
    void editorsKeepDistinctMessageComparisonRules() {
        NegotiationStep current = NegotiationStep.builder().stepNumber(1)
                .offerPrice(new BigDecimal("500.00"))
                .maxAcceptedCounterOffer(new BigDecimal("600.00"))
                .message("  Hello   seller ").build();
        CreateNegotiationStepRequest requested = request("500.0", "600.000", "Hello seller");
        assertTrue(NegotiationStepPolicySupport.definitionChanged(
                List.of(current), List.of(requested), java.util.function.UnaryOperator.identity()));
        assertFalse(NegotiationStepPolicySupport.definitionChanged(
                List.of(current), List.of(requested),
                text -> text == null ? "" : text.trim().replaceAll("\\s+", " ")));
        requested.setMessage("hello seller");
        assertTrue(NegotiationStepPolicySupport.definitionChanged(
                List.of(current), List.of(requested),
                text -> text == null ? "" : text.trim().replaceAll("\\s+", " ")));
    }

    @Test
    void definitionComparisonDetectsMissingStepsWrongNumberOrChangedCounterLimit() {
        NegotiationStep current = NegotiationStep.builder().stepNumber(3)
                .offerPrice(new BigDecimal("500"))
                .maxAcceptedCounterOffer(new BigDecimal("600")).message("Hi").build();
        CreateNegotiationStepRequest requested = request("500.00", "600.0", "Hi");
        assertTrue(NegotiationStepPolicySupport.definitionChanged(
                List.of(current), null, java.util.function.UnaryOperator.identity()));
        assertTrue(NegotiationStepPolicySupport.definitionChanged(
                List.of(current), List.of(), java.util.function.UnaryOperator.identity()));
        assertTrue(NegotiationStepPolicySupport.definitionChanged(
                List.of(current), List.of(requested), java.util.function.UnaryOperator.identity()));
        current.setStepNumber(1);
        assertFalse(NegotiationStepPolicySupport.definitionChanged(
                List.of(current), List.of(requested), java.util.function.UnaryOperator.identity()));
        requested.setMaxAcceptedCounterOffer(new BigDecimal("601"));
        assertTrue(NegotiationStepPolicySupport.definitionChanged(
                List.of(current), List.of(requested), java.util.function.UnaryOperator.identity()));
    }

    @Test
    void adaptiveCapAndStrategyVersionRespectNullAndDecimalScale() {
        assertEquals(2, NegotiationStepPolicySupport.nextStrategyVersion(null));
        assertEquals(2, NegotiationStepPolicySupport.nextStrategyVersion(0));
        assertEquals(2, NegotiationStepPolicySupport.nextStrategyVersion(1));
        assertEquals(8, NegotiationStepPolicySupport.nextStrategyVersion(7));
        assertTrue(NegotiationStepPolicySupport.isGlobalCapIncreased(null, new BigDecimal("850")));
        assertFalse(NegotiationStepPolicySupport.isGlobalCapIncreased(
                new BigDecimal("850.0"), new BigDecimal("850.00")));
        assertFalse(NegotiationStepPolicySupport.isGlobalCapIncreased(new BigDecimal("900"), null));
        assertTrue(NegotiationStepPolicySupport.isGlobalCapIncreased(
                new BigDecimal("850"), new BigDecimal("900")));
        assertTrue(NegotiationStepPolicySupport.sameDecimal(
                new BigDecimal("850.00"), new BigDecimal("850")));
        assertFalse(NegotiationStepPolicySupport.sameDecimal(null, new BigDecimal("1")));
    }

    @Test
    void responsePoliciesReuseSameDefaultResolverWithoutFalseVersionChanges() {
        NegotiationStep step = step();
        step.setStepNumber(1);
        CreateNegotiationStepRequest req = request("500", "600", "Hi");
        NegotiationStepPolicySupport.applyPolicy(step, NegotiationPolicyDefaults.resolvePolicy(req, 1));
        assertFalse(NegotiationStepPolicySupport.responsePoliciesChanged(List.of(step), List.of(req)));
        assertTrue(NegotiationStepPolicySupport.responsePoliciesChanged(List.of(step), null));
        req.setRejectionAction(NegotiationReactionAction.WAIT_BEFORE_NEXT_STEP);
        req.setRejectionWaitHours(12);
        assertTrue(NegotiationStepPolicySupport.responsePoliciesChanged(List.of(step), List.of(req)));
    }

    private static CreateNegotiationStepRequest request(String offer, String cap, String message) {
        CreateNegotiationStepRequest req = new CreateNegotiationStepRequest();
        req.setOfferPrice(new BigDecimal(offer));
        req.setMaxAcceptedCounterOffer(new BigDecimal(cap));
        req.setMessage(message);
        return req;
    }

}
