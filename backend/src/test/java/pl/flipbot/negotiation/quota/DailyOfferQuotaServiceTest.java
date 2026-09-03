package pl.flipbot.negotiation.quota;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.flipbot.bot.Bot;
import pl.flipbot.bot.BotRepository;
import pl.flipbot.bot.configuration.BotConfiguration;
import pl.flipbot.negotiation.audit.RealActionAudit;
import pl.flipbot.negotiation.audit.RealActionAuditOutcome;
import pl.flipbot.negotiation.audit.RealActionAuditRepository;
import pl.flipbot.negotiation.quota.dto.DailyOfferQuotaResponse;
import pl.flipbot.negotiation.quota.dto.OfferQuotaReservationResponse;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DailyOfferQuotaServiceTest {

    private DailyOfferQuotaRepository quotaRepository;
    private BotRepository botRepository;
    private JdbcTemplate jdbcTemplate;
    private RealActionAuditRepository auditRepository;
    private DailyOfferQuotaService service;
    private Bot bot;

    @BeforeEach
    void setUp() {
        quotaRepository = mock(DailyOfferQuotaRepository.class);
        botRepository = mock(BotRepository.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        auditRepository = mock(RealActionAuditRepository.class);

        BotConfiguration configuration = BotConfiguration.builder()
                .dailyNegotiationBudget(25)
                .build();

        bot = Bot.builder()
                .id(3L)
                .configuration(configuration)
                .build();
        configuration.setBot(bot);

        when(botRepository.findById(3L)).thenReturn(Optional.of(bot));
        when(auditRepository.findAllByBotIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                eq(3L),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenReturn(List.of());

        service = new DailyOfferQuotaService(
                quotaRepository,
                botRepository,
                jdbcTemplate,
                auditRepository
        );
    }

    @Test
    void configuredNegotiationBudgetIsTheActualDailyOfferLimit() {
        bot.getConfiguration().setDailyNegotiationBudget(12);

        DailyOfferQuota quota = quota(5);
        when(quotaRepository.findByBot_IdAndUsageDate(
                eq(3L),
                any(LocalDate.class)
        )).thenReturn(Optional.of(quota));

        DailyOfferQuotaResponse response = service.getQuota(3L);

        assertEquals(12, response.limit());
        assertEquals(5, response.used());
        assertEquals(7, response.remaining());
    }

    @Test
    void auditedActionsRepairAQuotaUndercountConservatively() {
        DailyOfferQuota quota = quota(2);
        when(quotaRepository.findByBot_IdAndUsageDate(
                eq(3L),
                any(LocalDate.class)
        )).thenReturn(Optional.of(quota));
        when(auditRepository.findAllByBotIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                eq(3L),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenReturn(List.of(
                audit(RealActionAuditOutcome.CONFIRMED),
                audit(RealActionAuditOutcome.CONFIRMED),
                audit(RealActionAuditOutcome.AMBIGUOUS),
                audit(RealActionAuditOutcome.CONFIRMED)
        ));

        DailyOfferQuotaResponse response = service.getQuota(3L);

        assertEquals(4, response.used());
        assertEquals(21, response.remaining());
        verify(quotaRepository).save(quota);
    }

    @Test
    void reserveCannotExceedTheAuditedDailyFloorOrHardLimit() {
        DailyOfferQuota quota = quota(20);
        when(quotaRepository.findByBot_IdAndUsageDate(
                eq(3L),
                any(LocalDate.class)
        )).thenReturn(Optional.of(quota));
        when(auditRepository.findAllByBotIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                eq(3L),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenReturn(java.util.stream.IntStream.range(0, 25)
                .mapToObj(index -> audit(RealActionAuditOutcome.CONFIRMED))
                .toList());

        OfferQuotaReservationResponse response = service.reserveSlot(3L);

        assertFalse(response.reserved());
        assertEquals(25, response.used());
        assertEquals(0, response.remaining());
    }

    @Test
    void releaseNeverDropsBelowConfirmedOrAmbiguousAuditedActions() {
        DailyOfferQuota quota = quota(5);
        when(quotaRepository.findByBot_IdAndUsageDate(
                eq(3L),
                any(LocalDate.class)
        )).thenReturn(Optional.of(quota));
        when(auditRepository.findAllByBotIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                eq(3L),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenReturn(List.of(
                audit(RealActionAuditOutcome.CONFIRMED),
                audit(RealActionAuditOutcome.CONFIRMED),
                audit(RealActionAuditOutcome.CONFIRMED),
                audit(RealActionAuditOutcome.AMBIGUOUS),
                audit(RealActionAuditOutcome.CONFIRMED)
        ));

        DailyOfferQuotaResponse response = service.releaseSlot(3L);

        assertEquals(5, response.used());
        assertEquals(20, response.remaining());
    }

    @Test
    void releaseRemovesOnlyAnUnauditedReservation() {
        DailyOfferQuota quota = quota(5);
        when(quotaRepository.findByBot_IdAndUsageDate(
                eq(3L),
                any(LocalDate.class)
        )).thenReturn(Optional.of(quota));
        when(auditRepository.findAllByBotIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                eq(3L),
                any(LocalDateTime.class),
                any(LocalDateTime.class)
        )).thenReturn(List.of(
                audit(RealActionAuditOutcome.CONFIRMED),
                audit(RealActionAuditOutcome.CONFIRMED),
                audit(RealActionAuditOutcome.AMBIGUOUS),
                audit(RealActionAuditOutcome.CONFIRMED)
        ));

        DailyOfferQuotaResponse response = service.releaseSlot(3L);

        assertEquals(4, response.used());
        assertEquals(21, response.remaining());
        verify(quotaRepository).save(quota);
    }

    private DailyOfferQuota quota(int usedCount) {
        return DailyOfferQuota.builder()
                .bot(bot)
                .usageDate(LocalDate.now())
                .usedCount(usedCount)
                .build();
    }

    private RealActionAudit audit(
            RealActionAuditOutcome outcome
    ) {
        return RealActionAudit.builder()
                .botId(3L)
                .backendListingId(100L)
                .outcome(outcome)
                .build();
    }
}
