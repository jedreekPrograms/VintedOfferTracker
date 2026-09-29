package pl.flipbot.negotiation;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.flipbot.bot.Bot;
import pl.flipbot.bot.configuration.BotConfiguration;
import pl.flipbot.listing.ListingRepository;
import pl.flipbot.marketplace.Marketplace;
import pl.flipbot.negotiation.quota.dto.DailyOfferQuotaResponse;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NegotiationPlannerRollingCapacityTest {

    private static final long BOT_ID = 3L;
    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    @Test
    void userLadderAtFifteenAllowsEightStartsWithThreeSlotSafetyBuffer() {
        assertEquals(
                8,
                capacityAt("2026-09-29T13:00:00Z")
        );
    }

    @Test
    void sameLadderInMorningReservesMoreSameDayActions() {
        assertEquals(
                6,
                capacityAt("2026-09-29T06:00:00Z")
        );
    }

    @Test
    void lateEveningIsCappedByTomorrowsProjectedLoadNotOnlyTodaysFreeSlots() {
        /*
         * At 22:00 the weighted day-0 load is small, so a today-only planner
         * would admit far more conversations. The rolling model sees the large
         * D+1 bucket and limits the batch to six.
         */
        assertEquals(
                6,
                capacityAt("2026-09-29T20:00:00Z")
        );
    }

    private int capacityAt(String instant) {
        ListingRepository listingRepository = mock(ListingRepository.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        when(listingRepository.findByBotIdAndStatusOrderByIdAsc(
                BOT_ID,
                pl.flipbot.listing.ListingStatus.NEGOTIATING
        )).thenReturn(List.of());
        when(listingRepository.findByBotIdAndStatusOrderByIdAsc(
                BOT_ID,
                pl.flipbot.listing.ListingStatus.ACTION_REQUIRED
        )).thenReturn(List.of());

        Clock clock = Clock.fixed(
                Instant.parse(instant),
                WARSAW
        );

        NegotiationPlanner planner = new NegotiationPlanner(
                listingRepository,
                jdbcTemplate,
                clock
        );

        List<NegotiationStep> steps = userFiveStepLadder();
        BotConfiguration configuration = BotConfiguration.builder()
                .marketplace(Marketplace.VINTED)
                .dailyNegotiationBudget(25)
                .negotiationSteps(new ArrayList<>(steps))
                .build();
        Bot bot = Bot.builder()
                .id(BOT_ID)
                .configuration(configuration)
                .build();
        configuration.setBot(bot);

        return planner.calculateNewNegotiations(
                bot,
                new DailyOfferQuotaResponse(25, 0, 25),
                steps
        );
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
