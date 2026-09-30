package pl.flipbot.marketstats;

import org.junit.jupiter.api.Test;
import pl.flipbot.bot.Bot;
import pl.flipbot.bot.configuration.BotAdditionalTarget;
import pl.flipbot.bot.configuration.BotAdditionalTargetRepository;
import pl.flipbot.bot.configuration.BotConfiguration;
import pl.flipbot.bot.configuration.BotConfigurationRepository;
import pl.flipbot.bot.configuration.TargetMode;
import pl.flipbot.dictionary.DictionaryBrand;
import pl.flipbot.dictionary.DictionaryModel;
import pl.flipbot.dictionary.DictionaryModelRepository;
import pl.flipbot.marketstats.dto.CalendarModelPlanningResponse;
import pl.flipbot.negotiation.audit.RealActionAudit;
import pl.flipbot.negotiation.audit.RealActionAuditRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MarketStatsCalendarPlanningServiceTest {

    @Test
    void activeAdditionalTargetCountsAsExistingBotAndUsesObservedCapacity() {
        DictionaryModelRepository modelRepository =
                mock(DictionaryModelRepository.class);
        BotConfigurationRepository configurationRepository =
                mock(BotConfigurationRepository.class);
        BotAdditionalTargetRepository additionalTargetRepository =
                mock(BotAdditionalTargetRepository.class);
        MarketModelScanStateRepository scanStateRepository =
                mock(MarketModelScanStateRepository.class);
        MarketListingObservationRepository observationRepository =
                mock(MarketListingObservationRepository.class);
        RealActionAuditRepository realActionAuditRepository =
                mock(RealActionAuditRepository.class);

        DictionaryBrand samsung = DictionaryBrand.builder()
                .id(1L)
                .name("Samsung")
                .build();
        DictionaryModel s26 = DictionaryModel.builder()
                .id(30L)
                .brand(samsung)
                .name("Galaxy S26")
                .targetMode(TargetMode.VINTED_MODEL)
                .build();

        Bot bot = Bot.builder()
                .id(9L)
                .name("S26 multi")
                .marketStatsObserver(false)
                .build();
        BotConfiguration configuration = BotConfiguration.builder()
                .id(90L)
                .bot(bot)
                .brand("Samsung")
                .targetMode(TargetMode.VINTED_MODEL)
                .model("Galaxy S24")
                .build();
        BotAdditionalTarget additionalTarget = BotAdditionalTarget.builder()
                .id(901L)
                .configuration(configuration)
                .brand("Samsung")
                .targetMode(TargetMode.VINTED_MODEL)
                .model("Galaxy S26")
                .active(true)
                .build();

        List<RealActionAudit> audits = new ArrayList<>();
        LocalDateTime yesterday = LocalDateTime.now().minusDays(1);
        for (int index = 0; index < 8; index++) {
            audits.add(
                    RealActionAudit.builder()
                            .botId(9L)
                            .createdAt(yesterday.plusMinutes(index))
                            .build()
            );
        }

        when(modelRepository.findAll()).thenReturn(List.of(s26));
        when(configurationRepository.findAll()).thenReturn(List.of(configuration));
        when(additionalTargetRepository.findAll()).thenReturn(List.of(additionalTarget));
        when(scanStateRepository.findById(30L)).thenReturn(Optional.empty());
        when(realActionAuditRepository
                .findAllByActionTypeAndOutcomeAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(
                        any(),
                        any(),
                        any(LocalDateTime.class)
                ))
                .thenReturn(audits);

        MarketStatsCalendarPlanningService service =
                new MarketStatsCalendarPlanningService(
                        modelRepository,
                        configurationRepository,
                        additionalTargetRepository,
                        scanStateRepository,
                        observationRepository,
                        realActionAuditRepository
                );

        CalendarModelPlanningResponse response =
                service.getPlanning().getFirst();

        assertEquals(1, response.existingBots());
        assertEquals(8, response.dailyConversationCapacityPerBot());
        assertEquals(56, response.weeklyConversationCapacityPerBot());
    }
}
