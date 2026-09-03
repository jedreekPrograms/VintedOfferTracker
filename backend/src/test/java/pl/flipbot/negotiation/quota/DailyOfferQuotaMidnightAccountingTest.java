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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DailyOfferQuotaMidnightAccountingTest {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    private DailyOfferQuotaRepository quotaRepository;
    private DailyOfferQuotaReservationRepository reservationRepository;
    private RealActionAuditRepository auditRepository;
    private DailyOfferQuotaService service;
    private Bot bot;

    @BeforeEach
    void setUp() {
        quotaRepository = mock(DailyOfferQuotaRepository.class);
        reservationRepository = mock(DailyOfferQuotaReservationRepository.class);
        BotRepository botRepository = mock(BotRepository.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        auditRepository = mock(RealActionAuditRepository.class);

        BotConfiguration configuration = BotConfiguration.builder()
                .dailyNegotiationBudget(25)
                .build();
        bot = Bot.builder().id(3L).configuration(configuration).build();
        configuration.setBot(bot);

        when(botRepository.findById(3L)).thenReturn(Optional.of(bot));
        when(reservationRepository.findAllByBotIdAndUsageDateAndActiveTrue(
                eq(3L), any(LocalDate.class)
        )).thenReturn(List.of());

        service = new DailyOfferQuotaService(
                quotaRepository,
                reservationRepository,
                botRepository,
                jdbcTemplate,
                auditRepository
        );
    }

    @Test
    void confirmationAfterMidnightDoesNotChargeTheNextQuotaDay() {
        LocalDate today = LocalDate.now(WARSAW);
        UUID requestId = UUID.randomUUID();
        DailyOfferQuota todayQuota = quota(today, 0);
        DailyOfferQuotaReservation yesterdayReservation = reservation(
                requestId,
                today.minusDays(1),
                true
        );

        when(quotaRepository.findByBot_IdAndUsageDate(3L, today))
                .thenReturn(Optional.of(todayQuota));
        when(auditRepository.findAllByBotIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                eq(3L), any(LocalDateTime.class), any(LocalDateTime.class)
        )).thenReturn(List.of(audit(requestId)));
        when(reservationRepository.findById(requestId))
                .thenReturn(Optional.of(yesterdayReservation));

        DailyOfferQuotaResponse response = service.getQuota(3L);

        assertEquals(0, response.used());
        assertEquals(25, response.remaining());
    }

    @Test
    void auditUsesReservationDateAsSourceOfTruthEvenWhenReservationIsNoLongerActive() {
        LocalDate today = LocalDate.now(WARSAW);
        UUID requestId = UUID.randomUUID();
        DailyOfferQuota todayQuota = quota(today, 0);
        DailyOfferQuotaReservation todayReservation = reservation(
                requestId,
                today,
                false
        );

        when(quotaRepository.findByBot_IdAndUsageDate(3L, today))
                .thenReturn(Optional.of(todayQuota));
        when(auditRepository.findAllByBotIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                eq(3L), any(LocalDateTime.class), any(LocalDateTime.class)
        )).thenReturn(List.of(audit(requestId)));
        when(reservationRepository.findById(requestId))
                .thenReturn(Optional.of(todayReservation));

        DailyOfferQuotaResponse response = service.getQuota(3L);

        assertEquals(1, response.used());
        assertEquals(24, response.remaining());
        verify(quotaRepository).save(todayQuota);
    }

    private DailyOfferQuota quota(LocalDate usageDate, int used) {
        return DailyOfferQuota.builder()
                .bot(bot)
                .usageDate(usageDate)
                .usedCount(used)
                .build();
    }

    private DailyOfferQuotaReservation reservation(
            UUID requestId,
            LocalDate usageDate,
            boolean active
    ) {
        return DailyOfferQuotaReservation.builder()
                .requestId(requestId)
                .botId(3L)
                .usageDate(usageDate)
                .active(active)
                .createdAt(usageDate.atStartOfDay())
                .build();
    }

    private RealActionAudit audit(UUID requestId) {
        return RealActionAudit.builder()
                .requestId(requestId)
                .botId(3L)
                .backendListingId(100L)
                .outcome(RealActionAuditOutcome.CONFIRMED)
                .build();
    }
}
