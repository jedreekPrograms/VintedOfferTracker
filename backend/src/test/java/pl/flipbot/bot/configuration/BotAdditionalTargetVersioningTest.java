package pl.flipbot.bot.configuration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.flipbot.bot.Bot;
import pl.flipbot.bot.BotRepository;
import pl.flipbot.bot.BotStatus;
import pl.flipbot.bot.dto.UpsertBotAdditionalTargetRequest;
import pl.flipbot.listing.Listing;
import pl.flipbot.listing.ListingRepository;
import pl.flipbot.listing.ListingStatus;
import pl.flipbot.mapper.BotAdditionalTargetMapper;
import pl.flipbot.negotiation.NegotiationStep;
import pl.flipbot.negotiation.dto.CreateNegotiationStepRequest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BotAdditionalTargetVersioningTest {

    private static final long BOT_ID = 7L;
    private static final long TARGET_ID = 44L;

    private BotRepository botRepository;
    private BotAdditionalTargetRepository targetRepository;
    private ListingRepository listingRepository;
    private BotAdditionalTargetService service;
    private BotAdditionalTarget target;

    @BeforeEach
    void setUp() {
        botRepository = mock(BotRepository.class);
        targetRepository = mock(BotAdditionalTargetRepository.class);
        listingRepository = mock(ListingRepository.class);
        BotAdditionalTargetMapper mapper = mock(BotAdditionalTargetMapper.class);

        BotConfiguration main = BotConfiguration.builder()
                .id(10L)
                .dailyNegotiationBudget(25)
                .build();

        Bot bot = Bot.builder()
                .id(BOT_ID)
                .status(BotStatus.STOPPED)
                .configuration(main)
                .build();
        main.setBot(bot);

        target = BotAdditionalTarget.builder()
                .id(TARGET_ID)
                .configuration(main)
                .categoryPath(new ArrayList<>(List.of("Elektronika", "Telefony")))
                .brand("Samsung")
                .targetMode(TargetMode.VINTED_MODEL)
                .model("Galaxy S24")
                .minPrice(new BigDecimal("700.00"))
                .maxPrice(new BigDecimal("1800.00"))
                .autoRaiseOfferToVintedMinimum(true)
                .maxAutomaticOffer(new BigDecimal("1300.00"))
                .negotiationStrategyVersion(1)
                .active(true)
                .negotiationSteps(new ArrayList<>())
                .build();

        target.getNegotiationSteps().add(step(1, "900.00", "950.00", "v1-1"));
        target.getNegotiationSteps().add(step(2, "1000.00", "1100.00", "v1-2"));

        Listing active = Listing.builder()
                .id(99L)
                .status(ListingStatus.NEGOTIATING)
                .additionalTarget(target)
                .bot(bot)
                .build();

        when(botRepository.findById(BOT_ID)).thenReturn(Optional.of(bot));
        when(targetRepository.findByIdAndConfigurationBotId(TARGET_ID, BOT_ID))
                .thenReturn(Optional.of(target));
        when(listingRepository
                .findByBotIdAndStatusAndAdditionalTargetIdOrderByIdAsc(
                        BOT_ID,
                        ListingStatus.NEGOTIATING,
                        TARGET_ID
                )).thenReturn(List.of(active));
        when(listingRepository
                .findByBotIdAndStatusAndAdditionalTargetIdOrderByIdAsc(
                        BOT_ID,
                        ListingStatus.ACTION_REQUIRED,
                        TARGET_ID
                )).thenReturn(List.of());

        service = new BotAdditionalTargetService(
                botRepository,
                targetRepository,
                listingRepository,
                mapper
        );
    }

    @Test
    void activeConversationAllowsNewLadderAndMessageVersion() {
        UpsertBotAdditionalTargetRequest request = unchangedRequest();
        request.getNegotiationSteps().get(1)
                .setOfferPrice(new BigDecimal("1050.00"));
        request.getNegotiationSteps().get(1)
                .setMessage("v2-2");

        assertDoesNotThrow(() -> service.update(BOT_ID, TARGET_ID, request));

        assertEquals(2, target.getNegotiationStrategyVersion());
        assertEquals(
                0,
                new BigDecimal("1050.00").compareTo(
                        target.getNegotiationSteps().get(1).getOfferPrice()
                )
        );
        assertEquals("v2-2", target.getNegotiationSteps().get(1).getMessage());
    }

    @Test
    void activeConversationStillRejectsTargetIdentityChange() {
        UpsertBotAdditionalTargetRequest request = unchangedRequest();
        request.setModel("Galaxy S25");

        assertThrows(
                IllegalStateException.class,
                () -> service.update(BOT_ID, TARGET_ID, request)
        );

        assertEquals("Galaxy S24", target.getModel());
        assertEquals(1, target.getNegotiationStrategyVersion());
    }

    private UpsertBotAdditionalTargetRequest unchangedRequest() {
        UpsertBotAdditionalTargetRequest request =
                new UpsertBotAdditionalTargetRequest();
        request.setCategoryPath(new ArrayList<>(target.getCategoryPath()));
        request.setBrand(target.getBrand());
        request.setTargetMode(target.getTargetMode());
        request.setModel(target.getModel());
        request.setSearchQuery(target.getSearchQuery());
        request.setMinPrice(target.getMinPrice());
        request.setMaxPrice(target.getMaxPrice());
        request.setAutoRaiseOfferToVintedMinimum(
                target.getAutoRaiseOfferToVintedMinimum()
        );
        request.setMaxAutomaticOffer(target.getMaxAutomaticOffer());

        List<CreateNegotiationStepRequest> steps = target.getNegotiationSteps()
                .stream()
                .map(existing -> {
                    CreateNegotiationStepRequest step =
                            new CreateNegotiationStepRequest();
                    step.setOfferPrice(existing.getOfferPrice());
                    step.setMaxAcceptedCounterOffer(
                            existing.getMaxAcceptedCounterOffer()
                    );
                    step.setMessage(existing.getMessage());
                    step.setRejectionAction(existing.getRejectionAction());
                    step.setRejectionWaitHours(existing.getRejectionWaitHours());
                    step.setCounterOfferDefaultAction(
                            existing.getCounterOfferDefaultAction()
                    );
                    step.setCounterOfferDefaultWaitHours(
                            existing.getCounterOfferDefaultWaitHours()
                    );
                    step.setCounterOfferRules(new ArrayList<>());
                    return step;
                })
                .toList();

        request.setNegotiationSteps(new ArrayList<>(steps));
        return request;
    }

    private NegotiationStep step(
            int number,
            String offer,
            String accepted,
            String message
    ) {
        return NegotiationStep.builder()
                .stepNumber(number)
                .offerPrice(new BigDecimal(offer))
                .maxAcceptedCounterOffer(new BigDecimal(accepted))
                .message(message)
                .additionalTarget(target)
                .build();
    }
}
