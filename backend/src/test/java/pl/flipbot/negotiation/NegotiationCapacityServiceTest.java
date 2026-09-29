package pl.flipbot.negotiation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.flipbot.bot.Bot;
import pl.flipbot.bot.BotRepository;
import pl.flipbot.bot.configuration.BotAdditionalTarget;
import pl.flipbot.bot.configuration.BotAdditionalTargetRepository;
import pl.flipbot.bot.configuration.BotConfiguration;
import pl.flipbot.listing.Listing;
import pl.flipbot.listing.ListingRepository;
import pl.flipbot.listing.ListingStatus;
import pl.flipbot.marketplace.Marketplace;
import pl.flipbot.negotiation.quota.DailyOfferQuotaService;
import pl.flipbot.negotiation.quota.dto.DailyOfferQuotaResponse;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
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
    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");
    private static final Clock FIFTEEN_OCLOCK = Clock.fixed(
            Instant.parse("2026-09-29T13:00:00Z"),
            WARSAW
    );

    private BotRepository botRepository;
    private BotAdditionalTargetRepository additionalTargetRepository;
    private ListingRepository listingRepository;
    private JdbcTemplate jdbcTemplate;
    private DailyOfferQuotaService dailyOfferQuotaService;
    private NegotiationCapacityService service;
    private Bot bot;

    @BeforeEach
    void setUp() {
        botRepository = mock(BotRepository.class);
        additionalTargetRepository = mock(BotAdditionalTargetRepository.class);
        listingRepository = mock(ListingRepository.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        dailyOfferQuotaService = mock(DailyOfferQuotaService.class);

        NegotiationPlanner negotiationPlanner =
                new NegotiationPlanner(
                        listingRepository,
                        jdbcTemplate,
                        FIFTEEN_OCLOCK
                );

        service = new NegotiationCapacityService(
                botRepository,
                additionalTargetRepository,
                negotiationPlanner,
                dailyOfferQuotaService
        );

        BotConfiguration configuration = BotConfiguration.builder()
                .marketplace(Marketplace.VINTED)
                .dailyNegotiationBudget(25)
                .negotiationSteps(new ArrayList<>(userFiveStepLadder()))
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
    void freshDayUsesRollingRiskProfileInsteadOfReservingAllFiveStepsToday() {
        quota(25, 0);

        /*
         * At 15:00 this exact ladder produces a rounded rolling profile close
         * to D0=2.50, D1=2.00, D2=0.50. With a 22-slot planning budget
         * (25 hard limit minus 3 safety slots), eight conversations fit.
         */
        assertEquals(
                8,
                service.calculateCapacity(BOT_ID).allowedNewNegotiations()
        );
    }

    @Test
    void startedConversationsDynamicallyReserveOnlyTheirRemainingRollingLoad() {
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

        /*
         * The old planner returned zero because it reserved 4 future steps for
         * every row. The rolling planner keeps room for three more starts while
         * still accounting for today's and tomorrow's projected load.
         */
        assertEquals(
                3,
                service.calculateCapacity(BOT_ID).allowedNewNegotiations()
        );
    }

    @Test
    void completingActiveConversationsImmediatelyReleasesTheirVirtualFutureLoad() {
        quota(25, 15);
        activeListings(List.of(), List.of());

        assertEquals(
                2,
                service.calculateCapacity(BOT_ID).allowedNewNegotiations()
        );
    }

    @Test
    void negotiatingAndActionRequiredRowsBothRemainInRollingReservationModel() {
        quota(25, 0);
        activeListings(
                List.of(active(ListingStatus.NEGOTIATING, 2)),
                List.of(active(ListingStatus.ACTION_REQUIRED, 4))
        );

        assertEquals(
                8,
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
                8,
                service.calculateCapacity(BOT_ID).allowedNewNegotiations()
        );
    }

    @Test
    void missingCurrentStepFailsClosedWithoutBreakingHardQuotaAccounting() {
        quota(25, 0);
        activeListings(
                List.of(active(ListingStatus.NEGOTIATING, null)),
                List.of()
        );

        assertEquals(
                6,
                service.calculateCapacity(BOT_ID).allowedNewNegotiations()
        );
    }

    @Test
    void usedActionsAndRollingFutureReservationsAreBothCounted() {
        quota(25, 7);
        activeListings(
                List.of(active(ListingStatus.NEGOTIATING, 3)),
                List.of()
        );

        assertEquals(
                5,
                service.calculateCapacity(BOT_ID).allowedNewNegotiations()
        );
    }

    @Test
    void exhaustedHardQuotaAlwaysBlocksNewNegotiations() {
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

    @Test
    void additionalProductUsesOwnLadderAndSharedRollingReservations() {
        quota(25, 0);

        BotAdditionalTarget additionalTarget = BotAdditionalTarget.builder()
                .id(44L)
                .configuration(bot.getConfiguration())
                .active(true)
                .negotiationSteps(new ArrayList<>(List.of(
                        NegotiationStep.builder().stepNumber(1).build(),
                        NegotiationStep.builder().stepNumber(2).build()
                )))
                .build();

        when(additionalTargetRepository.findByIdAndConfigurationBotId(
                44L,
                BOT_ID
        )).thenReturn(Optional.of(additionalTarget));

        Listing activeAdditional = Listing.builder()
                .status(ListingStatus.NEGOTIATING)
                .currentStep(1)
                .bot(bot)
                .additionalTarget(additionalTarget)
                .build();
        activeListings(List.of(activeAdditional), List.of());

        assertEquals(
                10,
                service.calculateCapacity(BOT_ID, 44L).allowedNewNegotiations()
        );
    }

    @Test
    void inactiveAdditionalProductCannotStartNewNegotiations() {
        BotAdditionalTarget additionalTarget = BotAdditionalTarget.builder()
                .id(45L)
                .configuration(bot.getConfiguration())
                .active(false)
                .negotiationSteps(new ArrayList<>(List.of(
                        NegotiationStep.builder().stepNumber(1).build()
                )))
                .build();

        when(additionalTargetRepository.findByIdAndConfigurationBotId(
                45L,
                BOT_ID
        )).thenReturn(Optional.of(additionalTarget));

        assertEquals(
                0,
                service.calculateCapacity(BOT_ID, 45L).allowedNewNegotiations()
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
                .currentStepStartedAt(java.time.LocalDateTime.of(
                        2026, 9, 29, 15, 0
                ))
                .bot(bot)
                .build();
    }

    private List<NegotiationStep> userFiveStepLadder() {
        return List.of(
                step(
                        1,
                        6,
                        6,
                        rule(5, 5),
                        rule(10, 4),
                        immediateRule(15)
                ),
                step(
                        2,
                        6,
                        6,
                        rule(10, 4),
                        rule(15, 2),
                        immediateRule(20)
                ),
                step(
                        3,
                        8,
                        8,
                        rule(10, 4),
                        rule(15, 2),
                        immediateRule(20)
                ),
                step(
                        4,
                        16,
                        16,
                        rule(10, 12),
                        rule(15, 8),
                        immediateRule(25)
                ),
                step(
                        5,
                        24,
                        24,
                        rule(10, 15),
                        rule(15, 10),
                        immediateRule(25)
                )
        );
    }

    private NegotiationStep step(
            int number,
            int rejectionWait,
            int defaultCounterWait,
            SellerCounterOfferRule... rules
    ) {
        return NegotiationStep.builder()
                .stepNumber(number)
                .offerPrice(BigDecimal.valueOf(800L + number * 50L))
                .maxAcceptedCounterOffer(
                        BigDecimal.valueOf(850L + number * 50L)
                )
                .rejectionAction(
                        NegotiationReactionAction.WAIT_BEFORE_NEXT_STEP
                )
                .rejectionWaitHours(rejectionWait)
                .counterOfferDefaultAction(
                        NegotiationReactionAction.WAIT_BEFORE_NEXT_STEP
                )
                .counterOfferDefaultWaitHours(defaultCounterWait)
                .counterOfferRules(new ArrayList<>(List.of(rules)))
                .build();
    }

    private SellerCounterOfferRule rule(
            int minimumDiscount,
            int waitHours
    ) {
        return SellerCounterOfferRule.builder()
                .minimumDiscountPercent(BigDecimal.valueOf(minimumDiscount))
                .action(NegotiationReactionAction.WAIT_BEFORE_NEXT_STEP)
                .waitHours(waitHours)
                .build();
    }

    private SellerCounterOfferRule immediateRule(int minimumDiscount) {
        return SellerCounterOfferRule.builder()
                .minimumDiscountPercent(BigDecimal.valueOf(minimumDiscount))
                .action(NegotiationReactionAction.NEXT_STEP_NOW)
                .build();
    }
}
