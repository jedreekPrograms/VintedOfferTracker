package pl.flipbot.mapper;

import org.springframework.stereotype.Component;
import pl.flipbot.negotiation.NegotiationStep;
import pl.flipbot.negotiation.dto.NegotiationStepResponse;

@Component
public class NegotiationStepMapper {

    public NegotiationStepResponse map(NegotiationStep step) {

        return NegotiationStepResponse.builder()
                .stepNumber(step.getStepNumber())
                .offerPrice(step.getOfferPrice())
                .maxAcceptedCounterOffer(step.getMaxAcceptedCounterOffer())
                .message(step.getMessage())
                .build();
    }
}
