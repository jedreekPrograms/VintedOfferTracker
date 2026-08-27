package pl.flipbot.mapper;

import org.springframework.stereotype.Component;
import pl.flipbot.negotiation.NegotiationStep;
import pl.flipbot.negotiation.dto.NegotiationStepResponse;
import pl.flipbot.negotiation.dto.SellerCounterOfferRuleResponse;

@Component
public class NegotiationStepMapper {

    public NegotiationStepResponse map(NegotiationStep step) {
        return NegotiationStepResponse.builder()
                .stepNumber(step.getStepNumber())
                .offerPrice(step.getOfferPrice())
                .maxAcceptedCounterOffer(step.getMaxAcceptedCounterOffer())
                .message(step.getMessage())
                .rejectionAction(step.getRejectionAction())
                .rejectionWaitHours(step.getRejectionWaitHours())
                .counterOfferDefaultAction(step.getCounterOfferDefaultAction())
                .counterOfferDefaultWaitHours(step.getCounterOfferDefaultWaitHours())
                .counterOfferRules(
                        step.getCounterOfferRules()
                                .stream()
                                .map(rule -> SellerCounterOfferRuleResponse.builder()
                                        .minimumDiscountPercent(rule.getMinimumDiscountPercent())
                                        .action(rule.getAction())
                                        .waitHours(rule.getWaitHours())
                                        .build())
                                .toList()
                )
                .build();
    }
}
