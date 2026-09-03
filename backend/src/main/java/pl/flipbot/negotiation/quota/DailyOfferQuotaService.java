package pl.flipbot.negotiation.quota;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.flipbot.bot.Bot;
import pl.flipbot.bot.BotRepository;
import pl.flipbot.exception.BotNotFoundException;
import pl.flipbot.negotiation.audit.RealActionAudit;
import pl.flipbot.negotiation.audit.RealActionAuditOutcome;
import pl.flipbot.negotiation.audit.RealActionAuditRepository;
import pl.flipbot.negotiation.quota.dto.DailyOfferQuotaResponse;
import pl.flipbot.negotiation.quota.dto.OfferQuotaReservationResponse;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailyOfferQuotaService {

    private static final int HARD_MAX_DAILY_OFFER_LIMIT = 25;

    private static final ZoneId QUOTA_ZONE =
            ZoneId.of("Europe/Warsaw");

    private final DailyOfferQuotaRepository dailyOfferQuotaRepository;

    private final BotRepository botRepository;

    private final JdbcTemplate jdbcTemplate;

    private final RealActionAuditRepository realActionAuditRepository;

    @Transactional
    public DailyOfferQuotaResponse getQuota(
            Long botId
    ) {
        lockBotRow(botId);

        Bot bot = getBot(botId);
        int dailyLimit = resolveDailyLimit(bot);
        LocalDate today = getToday();
        int auditFloor = getAuditedUsageFloor(botId, today);

        DailyOfferQuota quota = dailyOfferQuotaRepository
                .findByBot_IdAndUsageDate(botId, today)
                .orElse(null);

        int used = reconcileToAuditFloor(
                bot,
                today,
                quota,
                auditFloor
        );

        return createQuotaResponse(
                dailyLimit,
                used
        );
    }

    @Transactional
    public OfferQuotaReservationResponse reserveSlot(
            Long botId
    ) {
        lockBotRow(botId);

        Bot bot = getBot(botId);
        int dailyLimit = resolveDailyLimit(bot);
        LocalDate today = getToday();
        int auditFloor = getAuditedUsageFloor(botId, today);

        DailyOfferQuota quota = dailyOfferQuotaRepository
                .findByBot_IdAndUsageDate(botId, today)
                .orElseGet(
                        () -> DailyOfferQuota.builder()
                                .bot(bot)
                                .usageDate(today)
                                .usedCount(0)
                                .build()
                );

        reconcileExistingQuotaToAuditFloor(
                quota,
                auditFloor
        );

        if (quota.getUsedCount() >= dailyLimit) {
            return createReservationResponse(
                    false,
                    dailyLimit,
                    quota.getUsedCount()
            );
        }

        quota.setUsedCount(
                quota.getUsedCount() + 1
        );

        dailyOfferQuotaRepository.save(quota);

        return createReservationResponse(
                true,
                dailyLimit,
                quota.getUsedCount()
        );
    }

    @Transactional
    public DailyOfferQuotaResponse releaseSlot(
            Long botId
    ) {
        lockBotRow(botId);

        Bot bot = getBot(botId);
        int dailyLimit = resolveDailyLimit(bot);
        LocalDate today = getToday();
        int auditFloor = getAuditedUsageFloor(botId, today);

        DailyOfferQuota quota = dailyOfferQuotaRepository
                .findByBot_IdAndUsageDate(botId, today)
                .orElse(null);

        if (quota == null) {
            int used = reconcileToAuditFloor(
                    bot,
                    today,
                    null,
                    auditFloor
            );
            return createQuotaResponse(
                    dailyLimit,
                    used
            );
        }

        reconcileExistingQuotaToAuditFloor(
                quota,
                auditFloor
        );

        if (quota.getUsedCount() > auditFloor) {
            quota.setUsedCount(
                    quota.getUsedCount() - 1
            );
            dailyOfferQuotaRepository.save(quota);
        } else {
            log.warn(
                    "[OFFER QUOTA] Refusing to release bot {} below audited daily floor {}. Persisted used={}",
                    botId,
                    auditFloor,
                    quota.getUsedCount()
            );
        }

        return createQuotaResponse(
                dailyLimit,
                quota.getUsedCount()
        );
    }

    private int reconcileToAuditFloor(
            Bot bot,
            LocalDate usageDate,
            DailyOfferQuota quota,
            int auditFloor
    ) {
        if (quota == null) {
            if (auditFloor <= 0) {
                return 0;
            }

            DailyOfferQuota repaired = DailyOfferQuota.builder()
                    .bot(bot)
                    .usageDate(usageDate)
                    .usedCount(auditFloor)
                    .build();
            dailyOfferQuotaRepository.save(repaired);

            log.error(
                    "[OFFER QUOTA] Missing daily quota row for bot {} on {} despite {} audited real actions. Recreated conservatively from audit.",
                    bot.getId(),
                    usageDate,
                    auditFloor
            );
            return auditFloor;
        }

        reconcileExistingQuotaToAuditFloor(
                quota,
                auditFloor
        );
        return quota.getUsedCount();
    }

    private void reconcileExistingQuotaToAuditFloor(
            DailyOfferQuota quota,
            int auditFloor
    ) {
        if (quota.getUsedCount() >= auditFloor) {
            return;
        }

        int previous = quota.getUsedCount();
        quota.setUsedCount(auditFloor);
        dailyOfferQuotaRepository.save(quota);

        log.error(
                "[OFFER QUOTA] Repaired undercount for bot {} on {} from {} to audited floor {}.",
                quota.getBot().getId(),
                quota.getUsageDate(),
                previous,
                auditFloor
        );
    }

    private int getAuditedUsageFloor(
            Long botId,
            LocalDate usageDate
    ) {
        LocalDateTime dayStart = usageDate.atStartOfDay();
        LocalDateTime nextDayStart = usageDate.plusDays(1).atStartOfDay();

        List<RealActionAudit> audits = realActionAuditRepository
                .findAllByBotIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        botId,
                        dayStart,
                        nextDayStart
                );

        long counted = audits.stream()
                .filter(audit -> audit.getOutcome() == RealActionAuditOutcome.CONFIRMED
                        || audit.getOutcome() == RealActionAuditOutcome.AMBIGUOUS)
                .count();

        if (counted > Integer.MAX_VALUE) {
            throw new IllegalStateException(
                    "Daily real-action audit count exceeds integer range for bot "
                            + botId
            );
        }

        return (int) counted;
    }

    private Bot getBot(Long botId) {
        return botRepository.findById(botId)
                .orElseThrow(
                        () -> new BotNotFoundException(botId)
                );
    }

    private int resolveDailyLimit(Bot bot) {
        if (bot.getConfiguration() == null
                || bot.getConfiguration().getDailyNegotiationBudget() == null) {
            return 0;
        }

        int configuredLimit =
                bot.getConfiguration().getDailyNegotiationBudget();

        if (configuredLimit <= 0) {
            return 0;
        }

        return Math.min(
                configuredLimit,
                HARD_MAX_DAILY_OFFER_LIMIT
        );
    }

    private void lockBotRow(
            Long botId
    ) {
        try {
            jdbcTemplate.queryForObject(
                    """
                    SELECT id
                    FROM bot
                    WHERE id = ?
                    FOR UPDATE
                    """,
                    Long.class,
                    botId
            );
        } catch (EmptyResultDataAccessException exception) {
            throw new BotNotFoundException(botId);
        }
    }

    private LocalDate getToday() {
        return LocalDate.now(QUOTA_ZONE);
    }

    private DailyOfferQuotaResponse createQuotaResponse(
            int limit,
            int used
    ) {
        return new DailyOfferQuotaResponse(
                limit,
                used,
                Math.max(limit - used, 0)
        );
    }

    private OfferQuotaReservationResponse createReservationResponse(
            boolean reserved,
            int limit,
            int used
    ) {
        return new OfferQuotaReservationResponse(
                reserved,
                limit,
                used,
                Math.max(limit - used, 0)
        );
    }
}
