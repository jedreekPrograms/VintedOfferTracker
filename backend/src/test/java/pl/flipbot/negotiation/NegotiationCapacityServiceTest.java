package pl.flipbot.negotiation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.flipbot.bot.Bot;
import pl.flipbot.bot.BotRepository;
import pl.flipbot.bot.configuration.BotConfiguration;
import pl.flipbot.listing.Listing;
import pl.flipbot.listing.ListingRepository;
import pl.flipbot.listing.ListingStatus;
import pl.flipbot.marketplace.Marketplace;
import pl.flipbot.negotiation.quota.DailyOfferQuotaService;
import pl.flipbot.negotiation.quota.dto.DailyOfferQuotaResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NegotiationCapacityServiceTest {

    private static final long BOT_ID = 3L;

    private BotRepository botRepository;
    private ListingRepository listingRepository;
    private JdbcTemplate jdbcTemplate;
    private DailyOfferQuotaService dailyOfferQuotaService;
    private NegotiationCapacityService service;
    private Bot bot;

    @BeforeEach
    void setUp() {
        botRepository = mock(BotRepository.class);
        listingRepository = mock(ListingRepository.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        dailyOfferQuotaService = mock(DailyOfferQuotaService.class);

        NegotiationPlanner negotiationPlanner =
                new NegotiationPlanner(
                        listingRepository,
                        jdbcTemplate
                );

        service = new NegotiationCapacityService(
                botRepository,
                negotiationPlanner,
                dailyOfferQuotaService
        );

        BotConfiguration configuration = BotConfiguration.builder()
                .marketplace(Marketplace.VINTED)
                .dailyNegotiationBudget(25)
                .negotiationSteps(new ArrayList<>(
                        List.of(
                                NegotiationStep.builder().stepNumber(1).build(),
                                NegotiationStep.builder().stepNumber(2).build(),
                                NegotiationStep.builder().stepNumber(3).build(),
                                NegotiationStep.builder().stepNumber(4).build(),
                                NegotiationStep.builder().stepNumber(5).build()
                        )
                ))
                .build();

        bot = Bot.builder()
                .id(BOT_ID)
                .configuration(configuration)
                .build();

        configuration.setBot(bot);
        when(botRepository.findById(BOT_ID)).thenReturn(Optional.of(bot));
        activeListings(List.of(), List.of());
    }

    @Test
    void freshDayAllowsFiveFullFiveStepNegotiations() {
        quota(25, 0);

        assertEquals(
                5,
                service.calculateCapacity(BOT_ID).allowedNewNegotiations()
        );
    }

    @Test
    void fiveStartedConversationsReserveTheirRemainingTwentySteps() {
        quota(25, 5);
        activeListings(
                List.of(
                        active(ListingStatus.NEGOTIATING, 1),
                        active(ListingStatus.NEGOTIATING, 1),
                        active(ListingStatus.NEGOTIATING, 1),
                        active(ListingStatus.NEGOTIATING, 1),
                        active(ListingStatus.NEGOTIATING, 1)
                ),
                List.of()
        );

        assertEquals(
                0,
                service.calculateCapacity(BOT_ID).allowedNewNegotiations()
        );
    }

    @Test
    void finishingFiveConversationsAfterThreeStepsFreesTenSlotsSameDay() {
        quota(25, 15);
        activeListings(List.of(), List.of());

        assertEquals(
                2,
                service.calculateCapacity(BOT_ID).allowedNewNegotiations()
        );
    }

    @Test
    void midnightReservesOnlyFutureStepsOfStillActiveConversations() {
        quota(25, 0);
        activeListings(
                List.of(
                        active(ListingStatus.NEGOTIATING, 3),
                        active(ListingStatus.NEGOTIATING, 3),
                        active(ListingStatus.NEGOTIATING, 3)
                ),
                List.of()
        );

        /*
         * Three active step-3 conversations reserve 3 * 2 = 6 future actions.
         * Fresh daily quota: 25 - 6 = 19. A new five-step conversation needs
         * five slots, therefore floor(19 / 5) = 3 new conversations.
         */
        assertEquals(
                3,
                service.calculateCapacity(BOT_ID).allowedNewNegotiations()
        );
    }

    @Test
    void negotiatingAndActionRequiredBothReserveFutureSteps() {
        quota(25, 0);
        activeListings(
                List.of(active(ListingStatus.NEGOTIATING, 2)),
                List.of(active(ListingStatus.ACTION_REQUIRED, 4))
        );

        /*
         * Step 2 reserves 3 future actions, step 4 reserves 1. 21 slots remain,
         * so four new five-step conversations fit.
         */
        assertEquals(
                4,
                service.calculateCapacity(BOT_ID).allowedNewNegotiations()
        );
    }

    @Test
    void confirmedActiveDuplicateLoserDoesNotReserveFutureCapacity() {
        quota(25, 0);

        Listing duplicateLoser = Listing.builder()
                .id(999L)
                .listingId("9755800886")
                .status(ListingStatus.NEGOTIATING)
                .currentStep(1)
                .bot(bot)
                .build();

        activeListings(
                List.of(duplicateLoser),
                List.of()
        );

        when(jdbcTemplate.queryForObject(
                anyString(),
                eq(Boolean.class),
                any(Object[].class)
        )).thenReturn(true);

        assertEquals(
                5,
                service.calculateCapacity(BOT_ID).allowedNewNegotiations()
        );
    }

    @Test
    void missingCurrentStepFailsSafeByReservingWholeConversation() {
        quota(25, 0);
        activeListings(
                List.of(active(ListingStatus.NEGOTIATING, null)),
                List.of()
        );

        assertEquals(
                4,
                service.calculateCapacity(BOT_ID).allowedNewNegotiations()
        );
    }

    @Test
    void usedActionsAndFutureReservationsAreBothSubtracted() {
        quota(25, 7);
        activeListings(
                List.of(active(ListingStatus.NEGOTIATING, 3)),
                List.of()
        );

        /* remaining today=18, active future reservation=2, free=16, 16/5=3 */
        assertEquals(
                3,
                service.calculateCapacity(BOT_ID).allowedNewNegotiations()
        );
    }

    @Test
    void exhaustedQuotaBlocksNewNegotiations() {
        quota(25, 25);

        assertEquals(
                0,
                service.calculateCapacity(BOT_ID).allowedNewNegotiations()
        );
    }

    @Test
    void missingNegotiationStepsFailsClosedBeforeQuotaLookup() {
        bot.getConfiguration().setNegotiationSteps(new ArrayList<>());

        assertEquals(
                0,
                service.calculateCapacity(BOT_ID).allowedNewNegotiations()
        );

        verifyNoInteractions(dailyOfferQuotaService);
    }

    private void quota(int limit, int used) {
        when(dailyOfferQuotaService.getQuota(BOT_ID))
                .thenReturn(new DailyOfferQuotaResponse(
                        limit,
                        used,
                        Math.max(limit - used, 0)
                ));
    }

    private void activeListings(
            List<Listing> negotiating,
            List<Listing> actionRequired
    ) {
        when(listingRepository.findByBotIdAndStatusOrderByIdAsc(
                BOT_ID,
                ListingStatus.NEGOTIATING
        )).thenReturn(negotiating);
        when(listingRepository.findByBotIdAndStatusOrderByIdAsc(
                BOT_ID,
                ListingStatus.ACTION_REQUIRED
        )).thenReturn(actionRequired);
    }

    private Listing active(
            ListingStatus status,
            Integer currentStep
    ) {
        return Listing.builder()
                .status(status)
                .currentStep(currentStep)
                .bot(bot)
                .build();
    }
}
