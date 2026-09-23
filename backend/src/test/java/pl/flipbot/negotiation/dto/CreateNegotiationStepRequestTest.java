package pl.flipbot.negotiation.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreateNegotiationStepRequestTest {

    @Test
    void acceptedCounterofferCannotBeBelowOwnOffer() {
        CreateNegotiationStepRequest request = new CreateNegotiationStepRequest();
        request.setOfferPrice(new BigDecimal("1190"));
        request.setMaxAcceptedCounterOffer(new BigDecimal("1180"));

        assertFalse(request.isAcceptedCounterOfferCompatibleWithOwnOffer());
    }

    @Test
    void acceptedCounterofferMayEqualOrExceedOwnOffer() {
        CreateNegotiationStepRequest request = new CreateNegotiationStepRequest();
        request.setOfferPrice(new BigDecimal("1190"));

        request.setMaxAcceptedCounterOffer(new BigDecimal("1190"));
        assertTrue(request.isAcceptedCounterOfferCompatibleWithOwnOffer());

        request.setMaxAcceptedCounterOffer(new BigDecimal("1250"));
        assertTrue(request.isAcceptedCounterOfferCompatibleWithOwnOffer());
    }

    @Test
    void nullFieldsAreLeftToNotNullValidation() {
        CreateNegotiationStepRequest request = new CreateNegotiationStepRequest();

        assertTrue(request.isAcceptedCounterOfferCompatibleWithOwnOffer());
    }
}
