package pl.flipbot.negotiation.quota;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.flipbot.bot.Bot;
import pl.flipbot.bot.BotRepository;
import pl.flipbot.bot.configuration.BotConfiguration;
import pl.flipbot.negotiation.quota.dto.DailyOfferQuotaResponse;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DailyOfferQuotaServiceTest {

    @Test
    void configuredNegotiationBudgetIsTheActualDailyOfferLimit() {
        DailyOfferQuotaRepository quotaRepository =
                mock(DailyOfferQuotaRepository.class);
        BotRepository botRepository = mock(BotRepository.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        BotConfiguration configuration = BotConfiguration.builder()
                .dailyNegotiationBudget(12)
                .build();

        Bot bot = Bot.builder()
                .id(3L)
                .configuration(configuration)
                .build();
        configuration.setBot(bot);

        DailyOfferQuota quota = DailyOfferQuota.builder()
                .bot(bot)
                .usageDate(LocalDate.now())
                .usedCount(5)
                .build();

        when(botRepository.findById(3L)).thenReturn(Optional.of(bot));
        when(quotaRepository.findByBot_IdAndUsageDate(
                eq(3L),
                any(LocalDate.class)
        )).thenReturn(Optional.of(quota));

        DailyOfferQuotaService service = new DailyOfferQuotaService(
                quotaRepository,
                botRepository,
                jdbcTemplate
        );

        DailyOfferQuotaResponse response = service.getQuota(3L);

        assertEquals(12, response.limit());
        assertEquals(5, response.used());
        assertEquals(7, response.remaining());
    }
}
