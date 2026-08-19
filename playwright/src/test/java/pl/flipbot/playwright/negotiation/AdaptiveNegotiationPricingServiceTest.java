package pl.flipbot.playwright.negotiation;

import org.junit.Test;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.model.BotConfigurationDto;
import pl.flipbot.playwright.model.NegotiationStepDto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AdaptiveNegotiationPricingServiceTest {

    private final AdaptiveNegotiationPricingService service =
            new AdaptiveNegotiationPricingService();

    @Test
    public void twoThousandListingRaisesFirstRetryFromNineHundredToTwelveFifty() {
        BotConfigurationDto configuration = adaptiveConfiguration();

        Optional<BigDecimal> effective = service.firstAdaptiveRetryPrice(
                listing(
                        "DISCOVERED",
                        new BigDecimal("2000.00"),
                        new BigDecimal("2000.00"),
                        0
                ),
                configuration,
                new BigDecimal("900.00")
        );

        assertTrue(effective.isPresent());
        assertEquals(
                0,
                new BigDecimal("1250.00").compareTo(effective.get())
        );
    }

    @Test
    public void exactVintedMinimumStillRoundsStrictlyIntoNextFiftyBucket() {
        assertEquals(
                0,
                new BigDecimal("1250").compareTo(
                        AdaptiveNegotiationPricingService.roundStrictlyUp(
                                new BigDecimal("1200"),
                                new BigDecimal("50")
                        )
                )
        );
    }

    @Test
    public void secondStepPreservesConfiguredElevenPercentIncrease() {
        BotConfigurationDto configuration = adaptiveConfiguration();
        NegotiationStepDto configuredSecond = configuration
                .getNegotiationSteps()
                .get(1);

        Optional<NegotiationStepDto> effective = service.adaptNextStep(
                listing(
                        "NEGOTIATING",
                        new BigDecimal("2000.00"),
                        new BigDecimal("1250.00"),
                        1
                ),
                configuredSecond,
                configuration
        );

        assertTrue(effective.isPresent());
        assertEquals(
                0,
                new BigDecimal("1390.00").compareTo(
                        effective.get().getOfferPrice()
                )
        );
        assertEquals(
                0,
                new BigDecimal("1460.00").compareTo(
                        effective.get().getMaxAcceptedCounterOffer()
                )
        );
        assertEquals("step 2", effective.get().getMessage());
    }

    @Test
    public void thirdStepIsScaledFromActuallySentSecondStepAndAcceptanceIsCapped() {
        BotConfigurationDto configuration = adaptiveConfiguration();
        NegotiationStepDto configuredThird = configuration
                .getNegotiationSteps()
                .get(2);

        Optional<NegotiationStepDto> effective = service.adaptNextStep(
                listing(
                        "NEGOTIATING",
                        new BigDecimal("2000.00"),
                        new BigDecimal("1390.00"),
                        2
                ),
                configuredThird,
                configuration
        );

        assertTrue(effective.isPresent());
        assertEquals(
                0,
                new BigDecimal("1460.00").compareTo(
                        effective.get().getOfferPrice()
                )
        );
        assertEquals(
                0,
                new BigDecimal("1500.00").compareTo(
                        effective.get().getMaxAcceptedCounterOffer()
                )
        );
    }

    @Test
    public void firstStepAcceptedCounterOfferScalesWithActualFirstOffer() {
        BotConfigurationDto configuration = adaptiveConfiguration();

        BigDecimal effectiveLimit = service.effectiveAcceptedCounterOfferLimit(
                listing(
                        "NEGOTIATING",
                        new BigDecimal("2000.00"),
                        new BigDecimal("1250.00"),
                        1
                ),
                configuration.getNegotiationSteps().get(0),
                configuration
        );

        assertEquals(
                0,
                new BigDecimal("1320.00").compareTo(effectiveLimit)
        );
    }

    @Test
    public void nextStepAboveGlobalCapIsNotGenerated() {
        BotConfigurationDto configuration = adaptiveConfiguration();
        NegotiationStepDto stepFour = step(
                4,
                "1200.00",
                "1250.00",
                "step 4"
        );

        configuration.setNegotiationSteps(
                List.of(
                        configuration.getNegotiationSteps().get(0),
                        configuration.getNegotiationSteps().get(1),
                        configuration.getNegotiationSteps().get(2),
                        stepFour
                )
        );

        Optional<NegotiationStepDto> effective = service.adaptNextStep(
                listing(
                        "NEGOTIATING",
                        new BigDecimal("2000.00"),
                        new BigDecimal("1460.00"),
                        3
                ),
                stepFour,
                configuration
        );

        assertFalse(effective.isPresent());
    }

    @Test
    public void disabledAdaptiveModeKeepsConfiguredStepExactly() {
        BotConfigurationDto configuration = adaptiveConfiguration();
        configuration.setAutoRaiseOfferToVintedMinimum(false);
        configuration.setMaxAutomaticOffer(null);

        NegotiationStepDto configuredSecond = configuration
                .getNegotiationSteps()
                .get(1);

        Optional<NegotiationStepDto> effective = service.adaptNextStep(
                listing(
                        "NEGOTIATING",
                        new BigDecimal("2000.00"),
                        new BigDecimal("900.00"),
                        1
                ),
                configuredSecond,
                configuration
        );

        assertTrue(effective.isPresent());
        assertEquals(
                0,
                new BigDecimal("1000.00").compareTo(
                        effective.get().getOfferPrice()
                )
        );
        assertEquals(
                0,
                new BigDecimal("1050.00").compareTo(
                        effective.get().getMaxAcceptedCounterOffer()
                )
        );
    }

    private BotConfigurationDto adaptiveConfiguration() {
        BotConfigurationDto configuration = new BotConfigurationDto();
        configuration.setAutoRaiseOfferToVintedMinimum(true);
        configuration.setMaxAutomaticOffer(new BigDecimal("1500.00"));
        configuration.setNegotiationSteps(
                List.of(
                        step(1, "900.00", "950.00", "step 1"),
                        step(2, "1000.00", "1050.00", "step 2"),
                        step(3, "1050.00", "1100.00", "step 3")
                )
        );
        return configuration;
    }

    private NegotiationStepDto step(
            int stepNumber,
            String offerPrice,
            String acceptedCounterOffer,
            String message
    ) {
        NegotiationStepDto step = new NegotiationStepDto();
        step.setStepNumber(stepNumber);
        step.setOfferPrice(new BigDecimal(offerPrice));
        step.setMaxAcceptedCounterOffer(new BigDecimal(acceptedCounterOffer));
        step.setMessage(message);
        return step;
    }

    private ListingResponseDto listing(
            String status,
            BigDecimal originalPrice,
            BigDecimal currentPrice,
            int currentStep
    ) {
        return new ListingResponseDto(
                100L,
                "9700000000",
                "Samsung Galaxy S25",
                "https://www.vinted.pl/items/9700000000-samsung-galaxy-s25",
                originalPrice,
                currentPrice,
                currentStep,
                false,
                status.equals("NEGOTIATING") ? "12345" : null,
                status.equals("NEGOTIATING")
                        ? "https://www.vinted.pl/inbox/12345"
                        : null,
                status,
                null
        );
    }
}
