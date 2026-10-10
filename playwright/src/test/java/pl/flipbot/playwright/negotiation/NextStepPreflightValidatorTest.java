package pl.flipbot.playwright.negotiation;

import org.junit.Test;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.model.BotConfigurationDto;
import pl.flipbot.playwright.model.BotDetailsDto;
import pl.flipbot.playwright.model.NegotiationStepDto;

import java.math.BigDecimal;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class NextStepPreflightValidatorTest {

    private final BotContext context = mock(BotContext.class);
    private final NextStepPreflightValidator validator =
            new NextStepPreflightValidator(context);

    @Test
    public void rejectingNonNegotiatingAndIncompleteConversationPreservesOrdering() {
        ListingResponseDto listing = listing("DISCOVERED", 2, "1000.00");
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> validator.validateListing(listing)).getMessage()
                .contains("NEGOTIATING"));
        ListingResponseDto negotiating = listing("NEGOTIATING", 2, "1000.00");
        // Missing conversation evidence fails before interacting with Vinted.
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> validator.validateListing(negotiating)).getMessage()
                .contains("conversation ID"));
    }

    @Test
    public void nextStepNumberMustBePresentAndGreaterThanCurrent() {
        ListingResponseDto listing = listing("NEGOTIATING", 2, "1000.00");
        NegotiationStepDto step = new NegotiationStepDto();
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> validator.validateNextStep(listing, step)).getMessage()
                .contains("no step number"));
        step.setStepNumber(2);
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> validator.validateNextStep(listing, step)).getMessage()
                .contains("must be greater"));
    }

    @Test
    public void priceMustBePositiveAndNeverBelowCurrentOffer() {
        ListingResponseDto listing = listing("NEGOTIATING", 2, "1000.00");
        NegotiationStepDto step = step(3, null);
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> validator.validateNextStep(listing, step)).getMessage()
                .contains("no offer price"));
        step.setOfferPrice(BigDecimal.ZERO);
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> validator.validateNextStep(listing, step)).getMessage()
                .contains("invalid offer price"));
        step.setOfferPrice(new BigDecimal("999.99"));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> validator.validateNextStep(listing, step)).getMessage()
                .contains("cannot be lower"));
    }

    @Test
    public void equalPriceWithoutAdaptiveCapIsRejectedButIncreaseIsAllowed() {
        ListingResponseDto listing = listing("NEGOTIATING", 2, "1000.00");
        NegotiationStepDto step = step(3, "1000.00");
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> validator.validateNextStep(listing, step)).getMessage()
                .contains("only after the adaptive global cap"));
        step.setOfferPrice(new BigDecimal("1000.01"));
        validator.validateNextStep(listing, step);
    }

    @Test
    public void positiveAdaptiveCapAloneAllowsPlateauAndNotBeforeIt() {
        BotConfigurationDto config = new BotConfigurationDto();
        config.setAutoRaiseOfferToVintedMinimum(true);
        config.setMaxAutomaticOffer(new BigDecimal("1200.00"));
        BotDetailsDto bot = new BotDetailsDto();
        bot.setConfiguration(config);
        when(context.getBot()).thenReturn(bot);
        ListingResponseDto listing = listing("NEGOTIATING", 2, "1200.00");
        NegotiationStepDto step = step(3, "1200.00");
        validator.validateNextStep(listing, step);
        assertTrue(validator.isAllowedAdaptiveCapPlateau(listing, step));
        assertFalse(validator.isAllowedAdaptiveCapPlateau(
                listing("NEGOTIATING", 2, "1100.00"), step));
        config.setMaxAutomaticOffer(BigDecimal.ZERO);
        assertFalse(validator.isAllowedAdaptiveCapPlateau(listing, step));
    }

    private static ListingResponseDto listing(String status, int currentStep, String price) {
        ListingResponseDto listing = mock(ListingResponseDto.class);
        when(listing.id()).thenReturn(90L);
        when(listing.status()).thenReturn(status);
        when(listing.currentStep()).thenReturn(currentStep);
        when(listing.currentPrice()).thenReturn(new BigDecimal(price));
        return listing;
    }

    private static NegotiationStepDto step(Integer stepNumber, String price) {
        NegotiationStepDto step = new NegotiationStepDto();
        step.setStepNumber(stepNumber);
        if (price != null) step.setOfferPrice(new BigDecimal(price));
        return step;
    }
}
