package pl.flipbot.marketstats;

import org.junit.jupiter.api.Test;
import pl.flipbot.bot.Bot;
import pl.flipbot.bot.configuration.BotConfiguration;
import pl.flipbot.bot.configuration.BotConfigurationRepository;
import pl.flipbot.bot.configuration.TargetMode;
import pl.flipbot.dictionary.DictionaryBrand;
import pl.flipbot.dictionary.DictionaryModel;
import pl.flipbot.dictionary.DictionaryModelRepository;
import pl.flipbot.marketstats.dto.CalendarModelPlanningResponse;
import pl.flipbot.negotiation.audit.RealActionAudit;
import pl.flipbot.negotiation.audit.RealActionAuditOutcome;
import pl.flipbot.negotiation.audit.RealActionAuditRepository;
import pl.flipbot.negotiation.guard.RealActionType;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MarketStatsCalendarPlanningServiceTest {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    @Test
    void calendarOfferCountsUseTheSameObservedWindowPopulationAsPreviousWeekCoverage() {
        DictionaryModelRepository modelRepository = mock(DictionaryModelRepository.class);
        BotConfigurationRepository configurationRepository = mock(BotConfigurationRepository.class);
        MarketModelScanStateRepository scanStateRepository = mock(MarketModelScanStateRepository.class);
        MarketListingObservationRepository observationRepository = mock(MarketListingObservationRepository.class);
        RealActionAuditRepository auditRepository = mock(RealActionAuditRepository.class);

        DictionaryBrand brand = DictionaryBrand.builder()
                .id(1L)
                .name("Samsung")
                .build();
        DictionaryModel model = DictionaryModel.builder()
                .id(10L)
                .brand(brand)
                .name("Galaxy S25")
                .targetMode(TargetMode.VINTED_MODEL)
                .build();

        Bot bot1 = Bot.builder().id(101L).marketStatsObserver(false).build();
        Bot bot2 = Bot.builder().id(102L).marketStatsObserver(false).build();

        BotConfiguration config1 = BotConfiguration.builder()
                .bot(bot1)
                .brand("Samsung")
                .model("Galaxy S25")
                .targetMode(TargetMode.VINTED_MODEL)
                .build();
        BotConfiguration config2 = BotConfiguration.builder()
                .bot(bot2)
                .brand("Samsung")
                .model("Galaxy S25")
                .targetMode(TargetMode.VINTED_MODEL)
                .build();

        LocalDateTime now = LocalDateTime.now(WARSAW);
        MarketStatsPlanningCalculator.CalendarWindows windows =
                MarketStatsPlanningCalculator.windows(now);

        MarketModelScanState state = MarketModelScanState.builder()
                .modelId(model.getId())
                .model(model)
                .initializedAt(windows.previousWeekStart().minusDays(1))
                .baselineCompleteAt(windows.previousWeekStart().minusMinutes(1))
                .baselineOfferCount(50)
                .lastScanAt(now)
                .lastSuccessfulScanAt(now)
                .lastScanComplete(true)
                .build();

        RealActionAudit observedAByBot1 = confirmedFirstOffer(
                101L,
                "a",
                windows.previousWeekStart().plusDays(1)
        );
        RealActionAudit duplicateObservedAByBot2 = confirmedFirstOffer(
                102L,
                "a",
                windows.previousWeekStart().plusDays(2)
        );
        RealActionAudit outsideObservedSet = confirmedFirstOffer(
                101L,
                "outside",
                windows.previousWeekStart().plusDays(3)
        );
        RealActionAudit otherBotObservedC = confirmedFirstOffer(
                999L,
                "c",
                windows.previousWeekStart().plusDays(4)
        );
        RealActionAudit currentWeekB = confirmedFirstOffer(
                101L,
                "b",
                now.minusSeconds(1)
        );

        when(modelRepository.findAll()).thenReturn(List.of(model));
        when(configurationRepository.findAll()).thenReturn(List.of(config1, config2));
        when(scanStateRepository.findById(model.getId())).thenReturn(Optional.of(state));
        when(observationRepository.countNewListingsBetween(
                eq(model.getId()),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenReturn(0L);
        when(observationRepository.findListingIdsObservedDuringWindow(
                eq(model.getId()),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenReturn(List.of("a", "b", "c"));
        when(auditRepository
                .findAllByActionTypeAndOutcomeAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(
                        eq(RealActionType.FIRST_OFFER),
                        eq(RealActionAuditOutcome.CONFIRMED),
                        any(LocalDateTime.class)
                )).thenReturn(List.of(
                observedAByBot1,
                duplicateObservedAByBot2,
                outsideObservedSet,
                otherBotObservedC,
                currentWeekB
        ));

        MarketStatsCalendarPlanningService service =
                new MarketStatsCalendarPlanningService(
                        modelRepository,
                        configurationRepository,
                        scanStateRepository,
                        observationRepository,
                        auditRepository
                );

        CalendarModelPlanningResponse result = service.getPlanning().getFirst();

        assertEquals(3, result.offersToday());
        assertEquals(3, result.offersCurrentWeek());
        assertEquals(3, result.offersPreviousFullWeek());
        assertEquals(1, result.negotiationsStartedPreviousFullWeek());
        assertEquals(2, result.existingBots());
        assertEquals(0.5, result.empiricalConversationsPerBotPreviousFullWeek(), 0.0001);
        assertEquals(6, result.recommendedBots());
        assertEquals(1, result.negotiationsStartedCurrentWeek());
        assertEquals(1, result.negotiationsStartedToday());
    }

    private RealActionAudit confirmedFirstOffer(
            Long botId,
            String marketplaceListingId,
            LocalDateTime createdAt
    ) {
        return RealActionAudit.builder()
                .botId(botId)
                .marketplaceListingId(marketplaceListingId)
                .actionType(RealActionType.FIRST_OFFER)
                .outcome(RealActionAuditOutcome.CONFIRMED)
                .createdAt(createdAt)
                .build();
    }
}
