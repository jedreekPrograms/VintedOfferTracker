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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DailyOfferQuotaServiceTest {

    private DailyOfferQuotaRepository quotaRepository;
    private DailyOfferQuotaReservationRepository reservationRepository;
    private BotRepository botRepository;
    private JdbcTemplate jdbcTemplate;
    private RealActionAuditRepository auditRepository;
    private DailyOfferQuotaService service;
    private Bot bot;

    @BeforeEach
    void setUp() {
        quotaRepository = mock(DailyOfferQuotaRepository.class);
        reservationRepository = mock(DailyOfferQuotaReservationRepository.class);
        botRepository = mock(BotRepository.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        auditRepository = mock(RealActionAuditRepository.class);

        BotConfiguration configuration = BotConfiguration.builder()
                .dailyNegotiationBudget(25)
                .build();

        bot = Bot.builder().id(3L).configuration(configuration).build();
        configuration.setBot(bot);

        when(botRepository.findById(3L)).thenReturn(Optional.of(bot));
        when(auditRepository.findAllByBotIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                eq(3L), any(LocalDateTime.class), any(LocalDateTime.class)
        )).thenReturn(List.of());
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
    void configuredNegotiationBudgetIsTheActualDailyOfferLimit() {
        bot.getConfiguration().setDailyNegotiationBudget(12);
        DailyOfferQuota quota = quota(5);
        when(quotaRepository.findByBot_IdAndUsageDate(eq(3L), any(LocalDate.class)))
                .thenReturn(Optional.of(quota));

        DailyOfferQuotaResponse response = service.getQuota(3L);

        assertEquals(12, response.limit());
        assertEquals(5, response.used());
        assertEquals(7, response.remaining());
    }

    @Test
    void auditAndReservationLedgerAreUnionedWithoutDoubleCountingRequestIds() {
        UUID auditedOnly = UUID.randomUUID();
        UUID overlap = UUID.randomUUID();
        UUID reservationOnly = UUID.randomUUID();
        DailyOfferQuota quota = quota(1);

        when(quotaRepository.findByBot_IdAndUsageDate(eq(3L), any(LocalDate.class)))
                .thenReturn(Optional.of(quota));
        when(auditRepository.findAllByBotIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                eq(3L), any(LocalDateTime.class), any(LocalDateTime.class)
        )).thenReturn(List.of(
                audit(auditedOnly, RealActionAuditOutcome.CONFIRMED),
                audit(overlap, RealActionAuditOutcome.AMBIGUOUS)
        ));
        when(reservationRepository.findAllByBotIdAndUsageDateAndActiveTrue(
                eq(3L), any(LocalDate.class)
        )).thenReturn(List.of(
                activeReservation(overlap),
                activeReservation(reservationOnly)
        ));

        DailyOfferQuotaResponse response = service.getQuota(3L);

        assertEquals(3, response.used());
        assertEquals(22, response.remaining());
        verify(quotaRepository).save(quota);
    }

    @Test
    void reservationReplayIsIdempotentAndDoesNotIncrementAgain() {
        UUID requestId = UUID.randomUUID();
        DailyOfferQuota quota = quota(7);
        DailyOfferQuotaReservation reservation = activeReservation(requestId);

        when(quotaRepository.findByBot_IdAndUsageDate(eq(3L), any(LocalDate.class)))
                .thenReturn(Optional.of(quota));
        when(reservationRepository.findById(requestId)).thenReturn(Optional.of(reservation));
        when(reservationRepository.findAllByBotIdAndUsageDateAndActiveTrue(
                eq(3L), any(LocalDate.class)
        )).thenReturn(List.of(reservation));

        OfferQuotaReservationResponse response = service.reserveSlot(3L, requestId);

        assertTrue(response.reserved());
        assertEquals(7, response.used());
        verify(reservationRepository, never()).saveAndFlush(any());
        verify(quotaRepository, never()).save(any());
    }

    @Test
    void reserveCannotExceedTheDurableDailyFloorOrHardLimit() {
        UUID requestId = UUID.randomUUID();
        DailyOfferQuota quota = quota(20);
        when(quotaRepository.findByBot_IdAndUsageDate(eq(3L), any(LocalDate.class)))
                .thenReturn(Optional.of(quota));
        when(auditRepository.findAllByBotIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                eq(3L), any(LocalDateTime.class), any(LocalDateTime.class)
        )).thenReturn(java.util.stream.IntStream.range(0, 25)
                .mapToObj(index -> audit(UUID.randomUUID(), RealActionAuditOutcome.CONFIRMED))
                .toList());

        OfferQuotaReservationResponse response = service.reserveSlot(3L, requestId);

        assertFalse(response.reserved());
        assertEquals(25, response.used());
        assertEquals(0, response.remaining());
    }

    @Test
    void repeatedReleaseOfInactiveReservationDoesNotDecrementAgain() {
        UUID requestId = UUID.randomUUID();
        DailyOfferQuota quota = quota(4);
        DailyOfferQuotaReservation reservation = activeReservation(requestId);
        reservation.setActive(false);
        reservation.setReleasedAt(LocalDateTime.now());

        when(quotaRepository.findByBot_IdAndUsageDate(eq(3L), any(LocalDate.class)))
                .thenReturn(Optional.of(quota));
        when(reservationRepository.findById(requestId)).thenReturn(Optional.of(reservation));

        DailyOfferQuotaResponse response = service.releaseSlot(3L, requestId);

        assertEquals(4, response.used());
        verify(quotaRepository, never()).save(any());
    }

    @Test
    void auditedReservationCannotBeReleased() {
        UUID requestId = UUID.randomUUID();
        DailyOfferQuota quota = quota(5);
        DailyOfferQuotaReservation reservation = activeReservation(requestId);
        RealActionAudit confirmed = audit(requestId, RealActionAuditOutcome.CONFIRMED);

        when(quotaRepository.findByBot_IdAndUsageDate(eq(3L), any(LocalDate.class)))
                .thenReturn(Optional.of(quota));
        when(reservationRepository.findById(requestId)).thenReturn(Optional.of(reservation));
        when(auditRepository.findByRequestId(requestId)).thenReturn(Optional.of(confirmed));
        when(auditRepository.findAllByBotIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                eq(3L), any(LocalDateTime.class), any(LocalDateTime.class)
        )).thenReturn(List.of(confirmed));
        when(reservationRepository.findAllByBotIdAndUsageDateAndActiveTrue(
                eq(3L), any(LocalDate.class)
        )).thenReturn(List.of(reservation));

        DailyOfferQuotaResponse response = service.releaseSlot(3L, requestId);

        assertEquals(5, response.used());
        assertTrue(reservation.isActive());
        verify(reservationRepository, never()).saveAndFlush(reservation);
    }

    @Test
    void releaseDeactivatesExactlyItsOwnUnauditedReservation() {
        UUID requestId = UUID.randomUUID();
        DailyOfferQuota quota = quota(5);
        DailyOfferQuotaReservation reservation = activeReservation(requestId);

        when(quotaRepository.findByBot_IdAndUsageDate(eq(3L), any(LocalDate.class)))
                .thenReturn(Optional.of(quota));
        when(reservationRepository.findById(requestId)).thenReturn(Optional.of(reservation));
        when(auditRepository.findByRequestId(requestId)).thenReturn(Optional.empty());
        when(reservationRepository.findAllByBotIdAndUsageDateAndActiveTrue(
                eq(3L), any(LocalDate.class)
        )).thenReturn(List.of(
                activeReservation(UUID.randomUUID()),
                activeReservation(UUID.randomUUID()),
                activeReservation(UUID.randomUUID()),
                activeReservation(UUID.randomUUID())
        ));

        DailyOfferQuotaResponse response = service.releaseSlot(3L, requestId);

        assertFalse(reservation.isActive());
        assertEquals(4, response.used());
        verify(reservationRepository).saveAndFlush(reservation);
        verify(quotaRepository).save(quota);
    }

    private DailyOfferQuota quota(int usedCount) {
        return DailyOfferQuota.builder()
                .bot(bot)
                .usageDate(LocalDate.now())
                .usedCount(usedCount)
                .build();
    }

    private DailyOfferQuotaReservation activeReservation(UUID requestId) {
        return DailyOfferQuotaReservation.builder()
                .requestId(requestId)
                .botId(3L)
                .usageDate(LocalDate.now())
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private RealActionAudit audit(
            UUID requestId,
            RealActionAuditOutcome outcome
    ) {
        return RealActionAudit.builder()
                .requestId(requestId)
                .botId(3L)
                .backendListingId(100L)
                .outcome(outcome)
                .build();
    }
}
