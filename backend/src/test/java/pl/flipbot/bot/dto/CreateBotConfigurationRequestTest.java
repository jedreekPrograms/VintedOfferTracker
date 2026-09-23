package pl.flipbot.bot.dto;

import org.junit.jupiter.api.Test;
import pl.flipbot.negotiation.dto.CreateNegotiationStepRequest;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreateBotConfigurationRequestTest {

    @Test
    void nonAdaptiveNegotiationLadderMustStillIncrease() {
        CreateBotConfigurationRequest request = new CreateBotConfigurationRequest();
        request.setAutoRaiseOfferToVintedMinimum(false);
        request.setNegotiationSteps(List.of(step("1200"), step("1100")));

        assertFalse(request.isNegotiationOfferLadderIncreasing());
    }

    @Test
    void increasingLadderIsAcceptedInBothModes() {
        CreateBotConfigurationRequest request = new CreateBotConfigurationRequest();
        request.setNegotiationSteps(List.of(step("1050"), step("1190")));

        request.setAutoRaiseOfferToVintedMinimum(false);
        assertTrue(request.isNegotiationOfferLadderIncreasing());

        request.setAutoRaiseOfferToVintedMinimum(true);
        assertTrue(request.isNegotiationOfferLadderIncreasing());
    }

    private CreateNegotiationStepRequest step(String price) {
        CreateNegotiationStepRequest step = new CreateNegotiationStepRequest();
        step.setOfferPrice(new BigDecimal(price));
        return step;
    }
}
