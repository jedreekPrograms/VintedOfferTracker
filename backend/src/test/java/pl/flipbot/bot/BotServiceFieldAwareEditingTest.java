package pl.flipbot.bot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.flipbot.bot.configuration.BotConfiguration;
import pl.flipbot.bot.configuration.BotConfigurationRepository;
import pl.flipbot.bot.configuration.TargetMode;
import pl.flipbot.bot.dto.CreateBotConfigurationRequest;
import pl.flipbot.bot.dto.UpdateBotRequest;
import pl.flipbot.listing.Listing;
import pl.flipbot.listing.ListingRepository;
import pl.flipbot.listing.ListingStatus;
import pl.flipbot.mapper.BotMapper;
import pl.flipbot.marketplace.Marketplace;
import pl.flipbot.negotiation.NegotiationStep;
import pl.flipbot.negotiation.dto.CreateNegotiationStepRequest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BotServiceFieldAwareEditingTest {

    private static final long BOT_ID = 7L;

    private BotRepository botRepository;
    private BotConfigurationRepository configurationRepository;
    private ListingRepository listingRepository;
    private BotMapper botMapper;
    private BotService service;
    private Bot bot;
    private BotConfiguration configuration;

    @BeforeEach
    void setUp() {
        botRepository = mock(BotRepository.class);
        configurationRepository = mock(BotConfigurationRepository.class);
        listingRepository = mock(ListingRepository.class);
        botMapper = mock(BotMapper.class);

        service = new BotService(
                botRepository,
                configurationRepository,
                listingRepository,
                botMapper
        );

        configuration = BotConfiguration.builder()
                .id(11L)
                .marketplace(Marketplace.VINTED)
                .categoryPath(new ArrayList<>(List.of("Elektronika", "Telefony")))
                .brand("Samsung")
                .targetMode(TargetMode.VINTED_MODEL)
                .model("Galaxy S25")
                .minPrice(new BigDecimal("1000.00"))
                .maxPrice(new BigDecimal("2500.00"))
                .autoRaiseOfferToVintedMinimum(true)
                .maxAutomaticOffer(new BigDecimal("1500.00"))
                .dailyNegotiationBudget(25)
                .negotiationSteps(new ArrayList<>())
                .build();

        addStep(1, "900.00", "950.00", "Pierwsza wiadomość");
        addStep(2, "1000.00", "1050.00", "Druga wiadomość");

        bot = Bot.builder()
                .id(BOT_ID)
                .name("S25 bot")
                .email("s25@example.com")
                .password("secret")
                .status(BotStatus.STOPPED)
                .configuration(configuration)
                .build();

        configuration.setBot(bot);

        when(botRepository.findById(BOT_ID)).thenReturn(Optional.of(bot));
        when(botRepository.existsByEmailAndIdNot("s25@example.com", BOT_ID))
                .thenReturn(false);
        when(listingRepository.findByBotIdAndStatusOrderByIdAsc(
                anyLong(),
                org.mockito.ArgumentMatchers.any(ListingStatus.class)
        )).thenReturn(List.of());
    }

    @Test
    void activeNegotiationAllowsSafeOperationalFieldsAndGlobalCap() {
        activeNegotiation("1250.00");

        UpdateBotRequest request = unchangedRequest();
        request.setName("S25 bot — ograniczony");
        request.getConfiguration().setMinPrice(new BigDecimal("1100.00"));
        request.getConfiguration().setMaxPrice(new BigDecimal("2400.00"));
        request.getConfiguration().setDailyNegotiationBudget(18);
        request.getConfiguration().setMaxAutomaticOffer(new BigDecimal("1400.00"));

        assertDoesNotThrow(() -> service.updateBot(BOT_ID, request));

        assertEquals("S25 bot — ograniczony", bot.getName());
        assertEquals(0, new BigDecimal("1100.00").compareTo(configuration.getMinPrice()));
        assertEquals(0, new BigDecimal("2400.00").compareTo(configuration.getMaxPrice()));
        assertEquals(18, configuration.getDailyNegotiationBudget());
        assertEquals(0, new BigDecimal("1400.00").compareTo(configuration.getMaxAutomaticOffer()));
    }

    @Test
    void activeNegotiationAllowsGlobalCapBelowAlreadySentOffer() {
        activeNegotiation("1390.00");

        UpdateBotRequest request = unchangedRequest();
        request.getConfiguration().setMaxAutomaticOffer(new BigDecimal("1000.00"));

        assertDoesNotThrow(() -> service.updateBot(BOT_ID, request));

        assertEquals(
                0,
                new BigDecimal("1000.00").compareTo(
                        configuration.getMaxAutomaticOffer()
                )
        );
    }

    @Test
    void activeNegotiationRejectsTargetChange() {
        activeNegotiation("1250.00");

        UpdateBotRequest request = unchangedRequest();
        request.getConfiguration().setModel("Galaxy S24");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.updateBot(BOT_ID, request)
        );

        assertTrue(exception.getMessage().contains("model"));
        assertEquals("Galaxy S25", configuration.getModel());
    }

    @Test
    void activeNegotiationRejectsNegotiationStepChange() {
        activeNegotiation("1250.00");

        UpdateBotRequest request = unchangedRequest();
        request.getConfiguration()
                .getNegotiationSteps()
                .get(1)
                .setOfferPrice(new BigDecimal("1010.00"));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.updateBot(BOT_ID, request)
        );

        assertTrue(exception.getMessage().contains("negotiation steps"));
        assertEquals(
                0,
                new BigDecimal("1000.00").compareTo(
                        configuration.getNegotiationSteps().get(1).getOfferPrice()
                )
        );
    }

    @Test
    void activeNegotiationRejectsVintedAccountChange() {
        activeNegotiation("1250.00");

        UpdateBotRequest request = unchangedRequest();
        request.setEmail("other@example.com");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.updateBot(BOT_ID, request)
        );

        assertTrue(exception.getMessage().contains("Vinted e-mail"));
        assertEquals("s25@example.com", bot.getEmail());
    }

    @Test
    void editCapabilitiesExposeFirstConfiguredOfferAsMinimumCap() {
        Listing negotiating = listing(ListingStatus.NEGOTIATING, "1250.00");
        Listing actionRequired = listing(ListingStatus.ACTION_REQUIRED, "1460.00");

        when(listingRepository.findByBotIdAndStatusOrderByIdAsc(
                BOT_ID,
                ListingStatus.NEGOTIATING
        )).thenReturn(List.of(negotiating));
        when(listingRepository.findByBotIdAndStatusOrderByIdAsc(
                BOT_ID,
                ListingStatus.ACTION_REQUIRED
        )).thenReturn(List.of(actionRequired));

        var capabilities = service.getEditCapabilities(BOT_ID);

        assertTrue(capabilities.isHasActiveNegotiations());
        assertEquals(
                0,
                new BigDecimal("900.00").compareTo(
                        capabilities.getMinimumNegotiationCap()
                )
        );
    }

    @Test
    void noActiveNegotiationsStillAllowsStructuralEdit() {
        UpdateBotRequest request = unchangedRequest();
        request.getConfiguration().setModel("Galaxy S24");
        request.getConfiguration()
                .getNegotiationSteps()
                .get(1)
                .setOfferPrice(new BigDecimal("1020.00"));

        assertDoesNotThrow(() -> service.updateBot(BOT_ID, request));

        assertEquals("Galaxy S24", configuration.getModel());
        assertEquals(
                0,
                new BigDecimal("1020.00").compareTo(
                        configuration.getNegotiationSteps().get(1).getOfferPrice()
                )
        );
    }

    private void activeNegotiation(String currentPrice) {
        when(listingRepository.findByBotIdAndStatusOrderByIdAsc(
                BOT_ID,
                ListingStatus.NEGOTIATING
        )).thenReturn(List.of(listing(ListingStatus.NEGOTIATING, currentPrice)));
        when(listingRepository.findByBotIdAndStatusOrderByIdAsc(
                BOT_ID,
                ListingStatus.ACTION_REQUIRED
        )).thenReturn(List.of());
    }

    private Listing listing(ListingStatus status, String currentPrice) {
        return Listing.builder()
                .id(100L)
                .listingId("9700000000-" + status)
                .title("Samsung Galaxy S25")
                .url("https://www.vinted.pl/items/9700000000")
                .originalPrice(new BigDecimal("2000.00"))
                .currentPrice(new BigDecimal(currentPrice))
                .currentStep(1)
                .awaitingSellerResponse(true)
                .status(status)
                .bot(bot)
                .build();
    }

    private UpdateBotRequest unchangedRequest() {
        UpdateBotRequest request = new UpdateBotRequest();
        request.setName(bot.getName());
        request.setEmail(bot.getEmail());
        request.setPassword("");
        request.setConfiguration(configurationRequest());
        return request;
    }

    private CreateBotConfigurationRequest configurationRequest() {
        CreateBotConfigurationRequest request = new CreateBotConfigurationRequest();
        request.setMarketplace(configuration.getMarketplace());
        request.setCategoryPath(new ArrayList<>(configuration.getCategoryPath()));
        request.setBrand(configuration.getBrand());
        request.setTargetMode(configuration.getTargetMode());
        request.setModel(configuration.getModel());
        request.setSearchQuery(configuration.getSearchQuery());
        request.setMinPrice(configuration.getMinPrice());
        request.setMaxPrice(configuration.getMaxPrice());
        request.setAutoRaiseOfferToVintedMinimum(
                configuration.getAutoRaiseOfferToVintedMinimum()
        );
        request.setMaxAutomaticOffer(configuration.getMaxAutomaticOffer());
        request.setDailyNegotiationBudget(configuration.getDailyNegotiationBudget());

        List<CreateNegotiationStepRequest> steps = configuration.getNegotiationSteps()
                .stream()
                .sorted(java.util.Comparator.comparing(NegotiationStep::getStepNumber))
                .map(step -> {
                    CreateNegotiationStepRequest stepRequest =
                            new CreateNegotiationStepRequest();
                    stepRequest.setOfferPrice(step.getOfferPrice());
                    stepRequest.setMaxAcceptedCounterOffer(
                            step.getMaxAcceptedCounterOffer()
                    );
                    stepRequest.setMessage(step.getMessage());
                    return stepRequest;
                })
                .toList();

        request.setNegotiationSteps(new ArrayList<>(steps));
        return request;
    }

    private void addStep(
            int stepNumber,
            String offerPrice,
            String acceptedCounter,
            String message
    ) {
        NegotiationStep step = NegotiationStep.builder()
                .id((long) stepNumber)
                .stepNumber(stepNumber)
                .offerPrice(new BigDecimal(offerPrice))
                .maxAcceptedCounterOffer(new BigDecimal(acceptedCounter))
                .message(message)
                .configuration(configuration)
                .build();

        configuration.getNegotiationSteps().add(step);
    }
}
