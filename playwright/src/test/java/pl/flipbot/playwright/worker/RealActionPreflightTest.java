package pl.flipbot.playwright.worker;

import org.junit.Test;
import pl.flipbot.playwright.api.listing.ListingClient;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.model.BotAdditionalTargetDto;
import pl.flipbot.playwright.model.BotConfigurationDto;
import pl.flipbot.playwright.model.BotDetailsDto;
import pl.flipbot.playwright.model.NegotiationStepDto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;

public class RealActionPreflightTest {

    @Test
    public void oneStepNegotiatingProductDoesNotBlockAnotherProductsRealNextStep() {
        BotDetailsDto bot = new BotDetailsDto();
        bot.setId(77L);
        bot.setConfiguration(configuration("MAIN", 3));

        BotAdditionalTargetDto oneStepInactive = additionalTarget(
                10L,
                false,
                "One step",
                1
        );
        BotAdditionalTargetDto multiStepActive = additionalTarget(
                20L,
                true,
                "Three steps",
                3
        );
        bot.setAdditionalTargets(List.of(oneStepInactive, multiStepActive));

        ListingClient listingClient = new FixedNegotiatingListingClient(List.of(
                negotiatingListing(100L, "listing-100", 1, 10L),
                negotiatingListing(200L, "listing-200", 1, 20L)
        ));

        RealActionPreflight.Result result = new RealActionPreflight().validate(
                bot,
                ScheduledJobType.NEGOTIATION_CHECK,
                listingClient,
                false,
                true
        );

        assertTrue(result.ready());
        assertTrue(result.failures().isEmpty());
        assertTrue(result.notes().stream().anyMatch(note ->
                note.contains("ADDITIONAL:10")
                        && note.contains("one-step negotiation ladder")
        ));
    }

    @Test
    public void singleOneStepNegotiationIsAValidCheckEvenWithoutNextOffer() {
        BotDetailsDto bot = new BotDetailsDto();
        bot.setId(78L);
        bot.setConfiguration(configuration("MAIN", 1));
        bot.setAdditionalTargets(List.of());

        ListingClient listingClient = new FixedNegotiatingListingClient(List.of(
                negotiatingListing(300L, "listing-300", 1, null)
        ));

        RealActionPreflight.Result result = new RealActionPreflight().validate(
                bot,
                ScheduledJobType.NEGOTIATION_CHECK,
                listingClient,
                false,
                true
        );

        assertTrue(result.ready());
        assertTrue(result.failures().isEmpty());
    }

    private BotAdditionalTargetDto additionalTarget(
            Long id,
            boolean active,
            String label,
            int stepCount
    ) {
        BotAdditionalTargetDto target = new BotAdditionalTargetDto();
        target.setAdditionalTargetId(id);
        target.setActive(active);
        fillConfiguration(target, label, stepCount);
        return target;
    }

    private BotConfigurationDto configuration(String label, int stepCount) {
        BotConfigurationDto configuration = new BotConfigurationDto();
        fillConfiguration(configuration, label, stepCount);
        configuration.setDailyNegotiationBudget(25);
        return configuration;
    }

    private void fillConfiguration(
            BotConfigurationDto configuration,
            String label,
            int stepCount
    ) {
        configuration.setMarketplace("VINTED");
        configuration.setCategoryPath(List.of("Elektronika", "Telefony"));
        configuration.setBrand("Brand " + label);
        configuration.setTargetMode("VINTED_MODEL");
        configuration.setModel("Model " + label);
        configuration.setMinPrice(new BigDecimal("100.00"));
        configuration.setMaxPrice(new BigDecimal("2000.00"));

        List<NegotiationStepDto> steps = new ArrayList<>();
        for (int index = 1; index <= stepCount; index++) {
            NegotiationStepDto step = new NegotiationStepDto();
            step.setStepNumber(index);
            step.setOfferPrice(BigDecimal.valueOf(100L * index));
            step.setMessage("Step " + index);
            steps.add(step);
        }
        configuration.setNegotiationSteps(steps);
    }

    private ListingResponseDto negotiatingListing(
            Long id,
            String listingId,
            int currentStep,
            Long additionalTargetId
    ) {
        return new ListingResponseDto(
                id,
                listingId,
                "Listing " + listingId,
                "https://example.test/" + listingId,
                new BigDecimal("2000.00"),
                new BigDecimal("1900.00"),
                currentStep,
                true,
                "conversation-" + listingId,
                "https://example.test/conversation/" + listingId,
                "NEGOTIATING",
                null,
                null,
                null,
                null,
                null,
                null,
                additionalTargetId
        );
    }

    private static final class FixedNegotiatingListingClient extends ListingClient {
        private final List<ListingResponseDto> listings;

        private FixedNegotiatingListingClient(List<ListingResponseDto> listings) {
            this.listings = listings;
        }

        @Override
        public List<ListingResponseDto> getNegotiatingListings(Long botId) {
            return listings;
        }
    }
}
