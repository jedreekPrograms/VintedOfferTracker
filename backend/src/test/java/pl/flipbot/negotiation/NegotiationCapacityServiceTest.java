package pl.flipbot.negotiation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.flipbot.bot.Bot;
import pl.flipbot.bot.BotRepository;
import pl.flipbot.bot.configuration.BotConfiguration;
import pl.flipbot.listing.Listing;
import pl.flipbot.listing.ListingRepository;
import pl.flipbot.listing.ListingStatus;
import pl.flipbot.negotiation.quota.DailyOfferQuotaService;
import pl.flipbot.negotiation.quota.dto.DailyOfferQuotaResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NegotiationCapacityServiceTest {

    private static final long BOT_ID = 3L;

    private BotRepository botRepository;
    private ListingRepository listingRepository;
    private DailyOfferQuotaService dailyOfferQuotaService;
    private NegotiationCapacityService service;
    private Bot bot;

    @BeforeEach
    void setUp() {
        botRepository = mock(BotRepository.class);
        listingRepository = mock(ListingRepository.class);
        dailyOfferQuotaService = mock(DailyOfferQuotaService.class);

        NegotiationPlanner negotiationPlanner = new NegotiationPlanner(listingRepository);
        service = new NegotiationCapacityService(botRepository, negotiationPlanner, dailyOfferQuotaService);

        BotConfiguration configuration = BotConfiguration.builder()
                .dailyNegotiationBudget(25)
                .negotiationSteps(new ArrayList<>(List.of(
                        NegotiationStep.builder().stepNumber(1).build(),
                        NegotiationStep.builder().stepNumber(2).build(),
                        NegotiationStep.builder().stepNumber(3).build(),
                        NegotiationStep.builder().stepNumber(4).build(),
                        NegotiationStep.builder().stepNumber(5).build()
                )))
                .build();

        bot = Bot.builder().id(BOT_ID).configuration(configuration).build();
        configuration.setBot(bot);
        when(botRepository.findById(BOT_ID)).thenReturn(Optional.of(bot));
        activeListings(List.of(), List.of());
    }

    @Test
    void freshDayAllowsFiveFullFiveStepNegotiations() {
        quota(25, 0);
        assertEquals(5, service.calculateCapacity(BOT_ID).allowedNewNegotiations());
    }

    @Test
    void fiveStartedConversationsReserveTheirRemainingTwentySteps() {
        quota(25, 5);
        activeListings(List.of(
                active(ListingStatus.NEGOTIATING, 1), active(ListingStatus.NEGOTIATING, 1),
                active(ListingStatus.NEGOTIATING, 1), active(ListingStatus.NEGOTIATING, 1),
                active(ListingStatus.NEGOTIATING, 1)
        ), List.of());
        assertEquals(0, service.calculateCapacity(BOT_ID).allowedNewNegotiations());
    }

    @Test
    void finishingFiveConversationsAfterThreeStepsFreesTenSlotsSameDay() {
        quota(25, 15);
        activeListings(List.of(), List.of());
        assertEquals(2, service.calculateCapacity(BOT_ID).allowedNewNegotiations());
    }

    @Test
    void midnightReservesOnlyFutureStepsOfStillActiveConversations() {
        quota(25, 0);
        activeListings(List.of(
                active(ListingStatus.NEGOTIATING, 3),
                active(ListingStatus.NEGOTIATING, 3),
                active(ListingStatus.NEGOTIATING, 3)
        ), List.of());
        assertEquals(3, service.calculateCapacity(BOT_ID).allowedNewNegotiations());
    }

    @Test
    void actionRequiredDoesNotReserveFutureAutomatedSteps() {
        quota(25, 0);
        activeListings(
                List.of(),
                List.of(active(ListingStatus.ACTION_REQUIRED, 1))
        );

        /* ACTION_REQUIRED is manual/terminal for automation, so all 25 slots remain. */
        assertEquals(5, service.calculateCapacity(BOT_ID).allowedNewNegotiations());
    }

    @Test
    void negotiatingStillReservesFutureStepsWhenActionRequiredExists() {
        quota(25, 0);
        activeListings(
                List.of(active(ListingStatus.NEGOTIATING, 2)),
                List.of(active(ListingStatus.ACTION_REQUIRED, 1))
        );

        /* Only NEGOTIATING step 2 reserves 3 actions: 22 / 5 = 4. */
        assertEquals(4, service.calculateCapacity(BOT_ID).allowedNewNegotiations());
    }

    @Test
    void missingCurrentStepFailsSafeByReservingWholeConversation() {
        quota(25, 0);
        activeListings(List.of(active(ListingStatus.NEGOTIATING, null)), List.of());
        assertEquals(4, service.calculateCapacity(BOT_ID).allowedNewNegotiations());
    }

    @Test
    void usedActionsAndFutureReservationsAreBothSubtracted() {
        quota(25, 7);
        activeListings(List.of(active(ListingStatus.NEGOTIATING, 3)), List.of());
        assertEquals(3, service.calculateCapacity(BOT_ID).allowedNewNegotiations());
    }

    @Test
    void exhaustedQuotaBlocksNewNegotiations() {
        quota(25, 25);
        assertEquals(0, service.calculateCapacity(BOT_ID).allowedNewNegotiations());
    }

    @Test
    void missingNegotiationStepsFailsClosedBeforeQuotaLookup() {
        bot.getConfiguration().setNegotiationSteps(new ArrayList<>());
        assertEquals(0, service.calculateCapacity(BOT_ID).allowedNewNegotiations());
        verifyNoInteractions(dailyOfferQuotaService);
    }

    private void quota(int limit, int used) {
        when(dailyOfferQuotaService.getQuota(BOT_ID)).thenReturn(
                new DailyOfferQuotaResponse(limit, used, Math.max(limit - used, 0))
        );
    }

    private void activeListings(List<Listing> negotiating, List<Listing> actionRequired) {
        when(listingRepository.findByBotIdAndStatusOrderByIdAsc(BOT_ID, ListingStatus.NEGOTIATING))
                .thenReturn(negotiating);
        when(listingRepository.findByBotIdAndStatusOrderByIdAsc(BOT_ID, ListingStatus.ACTION_REQUIRED))
                .thenReturn(actionRequired);
    }

    private Listing active(ListingStatus status, Integer currentStep) {
        return Listing.builder().status(status).currentStep(currentStep).bot(bot).build();
    }
}
