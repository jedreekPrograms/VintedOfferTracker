package pl.flipbot.playwright.negotiation;

import org.junit.Test;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.model.BotConfigurationDto;
import pl.flipbot.playwright.model.BotDetailsDto;
import pl.flipbot.playwright.model.NegotiationStepDto;

import java.math.BigDecimal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NextNegotiationStepExecutorCapPlateauTest {

    @Test
    public void equalPriceIsAllowedOnlyAtAdaptiveGlobalCap() {
        BotContext context = mock(BotContext.class);

        BotConfigurationDto configuration = new BotConfigurationDto();
        configuration.setAutoRaiseOfferToVintedMinimum(true);
        configuration.setMaxAutomaticOffer(new BigDecimal("1200.00"));

        BotDetailsDto bot = new BotDetailsDto();
        bot.setConfiguration(configuration);
        when(context.getBot()).thenReturn(bot);

        NextNegotiationStepExecutor executor =
                new NextNegotiationStepExecutor(context);

        ListingResponseDto listing = listing("1200.00", 3);
        NegotiationStepDto stepFour = step(4, "1200.00");

        assertTrue(
                executor.isAllowedAdaptiveCapPlateau(
                        listing,
                        stepFour
                )
        );

        stepFour.setOfferPrice(new BigDecimal("1190.00"));
        assertFalse(
                executor.isAllowedAdaptiveCapPlateau(
                        listing,
                        stepFour
                )
        );

        stepFour.setOfferPrice(new BigDecimal("1200.00"));
        configuration.setAutoRaiseOfferToVintedMinimum(false);
        assertFalse(
                executor.isAllowedAdaptiveCapPlateau(
                        listing,
                        stepFour
                )
        );
    }

    @Test
    public void equalPriceBeforeCapIsNotTreatedAsPlateau() {
        BotContext context = mock(BotContext.class);

        BotConfigurationDto configuration = new BotConfigurationDto();
        configuration.setAutoRaiseOfferToVintedMinimum(true);
        configuration.setMaxAutomaticOffer(new BigDecimal("1200.00"));

        BotDetailsDto bot = new BotDetailsDto();
        bot.setConfiguration(configuration);
        when(context.getBot()).thenReturn(bot);

        NextNegotiationStepExecutor executor =
                new NextNegotiationStepExecutor(context);

        assertFalse(
                executor.isAllowedAdaptiveCapPlateau(
                        listing("1100.00", 2),
                        step(3, "1100.00")
                )
        );
    }

    private ListingResponseDto listing(
            String currentPrice,
            int currentStep
    ) {
        return new ListingResponseDto(
                100L,
                "9700000000",
                "Samsung Galaxy S25",
                "https://www.vinted.pl/items/9700000000-samsung-galaxy-s25",
                new BigDecimal("2000.00"),
                new BigDecimal(currentPrice),
                currentStep,
                true,
                "12345",
                "https://www.vinted.pl/inbox/12345",
                "NEGOTIATING",
                null
        );
    }

    private NegotiationStepDto step(
            int stepNumber,
            String price
    ) {
        NegotiationStepDto step = new NegotiationStepDto();
        step.setStepNumber(stepNumber);
        step.setOfferPrice(new BigDecimal(price));
        return step;
    }
}
