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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailyOfferQuotaService {

    private static final int HARD_MAX_DAILY_OFFER_LIMIT = 25;
    private static final ZoneId QUOTA_ZONE = ZoneId.of("Europe/Warsaw");

    private final DailyOfferQuotaRepository dailyOfferQuotaRepository;
    private final DailyOfferQuotaReservationRepository reservationRepository;
    private final BotRepository botRepository;
    private final JdbcTemplate jdbcTemplate;
    private final RealActionAuditRepository realActionAuditRepository;

    @Transactional
    public DailyOfferQuotaResponse getQuota(Long botId) {
        lockBotRow(botId);

        Bot bot = getBot(botId);
        LocalDate today = getToday();
        DailyOfferQuota quota = dailyOfferQuotaRepository
                .findByBot_IdAndUsageDate(botId, today)
                .orElse(null);
        int usageFloor = getDurableUsageFloor(botId, today);
        int used = reconcileToUsageFloor(bot, today, quota, usageFloor);

        return createQuotaResponse(resolveDailyLimit(bot), used);
    }

    @Transactional
    public OfferQuotaReservationResponse reserveSlot(
            Long botId,
            UUID requestId
    ) {
        Objects.requireNonNull(requestId, "Quota request id cannot be null");
        lockBotRow(botId);

        Bot bot = getBot(botId);
        int dailyLimit = resolveDailyLimit(bot);
        LocalDate today = getToday();

        DailyOfferQuota quota = dailyOfferQuotaRepository
                .findByBot_IdAndUsageDate(botId, today)
                .orElseGet(() -> DailyOfferQuota.builder()
                        .bot(bot)
                        .usageDate(today)
                        .usedCount(0)
                        .build());

        reconcileExistingQuotaToUsageFloor(
                quota,
                getDurableUsageFloor(botId, today)
        );

        DailyOfferQuotaReservation existing = reservationRepository
                .findById(requestId)
                .orElse(null);

        if (existing != null) {
            validateReservationOwner(existing, botId);

            if (!existing.getUsageDate().equals(today)) {
                log.warn(
                        "[OFFER QUOTA] Refusing stale reservation replay across quota days. bot={}, requestId={}, originalDate={}, today={}",
                        botId,
                        requestId,
                        existing.getUsageDate(),
                        today
                );
                return createReservationResponse(
                        false,
                        dailyLimit,
                        quota.getUsedCount()
                );
            }

            if (existing.isActive()) {
                log.info(
                        "[OFFER QUOTA] Idempotent reservation replay for bot {} requestId={}. used={}/{}",
                        botId,
                        requestId,
                        quota.getUsedCount(),
                        dailyLimit
                );
                return createReservationResponse(
                        true,
                        dailyLimit,
                        quota.getUsedCount()
                );
            }

            log.warn(
                    "[OFFER QUOTA] Refusing replay of an already released reservation. bot={}, requestId={}",
                    botId,
                    requestId
            );
            return createReservationResponse(
                    false,
                    dailyLimit,
                    quota.getUsedCount()
            );
        }

        if (quota.getUsedCount() >= dailyLimit) {
            return createReservationResponse(
                    false,
                    dailyLimit,
                    quota.getUsedCount()
            );
        }

        reservationRepository.saveAndFlush(
                DailyOfferQuotaReservation.builder()
                        .requestId(requestId)
                        .botId(botId)
                        .usageDate(today)
                        .active(true)
                        .createdAt(LocalDateTime.now(QUOTA_ZONE))
                        .build()
        );

        quota.setUsedCount(quota.getUsedCount() + 1);
        dailyOfferQuotaRepository.save(quota);

        return createReservationResponse(
                true,
                dailyLimit,
                quota.getUsedCount()
        );
    }

    @Transactional
    public DailyOfferQuotaResponse releaseSlot(
            Long botId,
            UUID requestId
    ) {
        Objects.requireNonNull(requestId, "Quota request id cannot be null");
        lockBotRow(botId);

        Bot bot = getBot(botId);
        int dailyLimit = resolveDailyLimit(bot);
        DailyOfferQuotaReservation reservation = reservationRepository
                .findById(requestId)
                .orElse(null);

        if (reservation == null) {
            log.warn(
                    "[OFFER QUOTA] Idempotent release ignored because no reservation exists. bot={}, requestId={}",
                    botId,
                    requestId
            );
            return getQuotaForDateWithoutRelocking(bot, getToday());
        }

        validateReservationOwner(reservation, botId);
        LocalDate usageDate = reservation.getUsageDate();
        DailyOfferQuota quota = dailyOfferQuotaRepository
                .findByBot_IdAndUsageDate(botId, usageDate)
                .orElse(null);

        if (!reservation.isActive()) {
            int used = reconcileToUsageFloor(
                    bot,
                    usageDate,
                    quota,
                    getDurableUsageFloor(botId, usageDate)
            );
            return createQuotaResponse(dailyLimit, used);
        }

        if (realActionAuditRepository.findByRequestId(requestId).isPresent()) {
            int used = reconcileToUsageFloor(
                    bot,
                    usageDate,
                    quota,
                    getDurableUsageFloor(botId, usageDate)
            );
            log.warn(
                    "[OFFER QUOTA] Refusing to release audited real action. bot={}, requestId={}, used={}",
                    botId,
                    requestId,
                    used
            );
            return createQuotaResponse(dailyLimit, used);
        }

        reservation.setActive(false);
        reservation.setReleasedAt(LocalDateTime.now(QUOTA_ZONE));
        reservationRepository.saveAndFlush(reservation);

        int usageFloorAfterRelease = getDurableUsageFloor(botId, usageDate);

        if (quota == null) {
            int used = reconcileToUsageFloor(
                    bot,
                    usageDate,
                    null,
                    usageFloorAfterRelease
            );
            return createQuotaResponse(dailyLimit, used);
        }

        reconcileExistingQuotaToUsageFloor(quota, usageFloorAfterRelease);

        if (quota.getUsedCount() > usageFloorAfterRelease) {
            quota.setUsedCount(quota.getUsedCount() - 1);
            dailyOfferQuotaRepository.save(quota);
        } else {
            log.warn(
                    "[OFFER QUOTA] Release for bot {} requestId={} cannot reduce used below durable floor {}.",
                    botId,
                    requestId,
                    usageFloorAfterRelease
            );
        }

        return createQuotaResponse(dailyLimit, quota.getUsedCount());
    }

    private DailyOfferQuotaResponse getQuotaForDateWithoutRelocking(
            Bot bot,
            LocalDate usageDate
    ) {
        DailyOfferQuota quota = dailyOfferQuotaRepository
                .findByBot_IdAndUsageDate(bot.getId(), usageDate)
                .orElse(null);
        int used = reconcileToUsageFloor(
                bot,
                usageDate,
                quota,
                getDurableUsageFloor(bot.getId(), usageDate)
        );
        return createQuotaResponse(resolveDailyLimit(bot), used);
    }

    private int reconcileToUsageFloor(
            Bot bot,
            LocalDate usageDate,
            DailyOfferQuota quota,
            int usageFloor
    ) {
        if (quota == null) {
            if (usageFloor <= 0) {
                return 0;
            }

            DailyOfferQuota repaired = DailyOfferQuota.builder()
                    .bot(bot)
                    .usageDate(usageDate)
                    .usedCount(usageFloor)
                    .build();
            dailyOfferQuotaRepository.save(repaired);

            log.error(
                    "[OFFER QUOTA] Missing quota row for bot {} on {} despite durable usage floor {}. Recreated conservatively.",
                    bot.getId(),
                    usageDate,
                    usageFloor
            );
            return usageFloor;
        }

        reconcileExistingQuotaToUsageFloor(quota, usageFloor);
        return quota.getUsedCount();
    }

    private void reconcileExistingQuotaToUsageFloor(
            DailyOfferQuota quota,
            int usageFloor
    ) {
        if (quota.getUsedCount() >= usageFloor) {
            return;
        }

        int previous = quota.getUsedCount();
        quota.setUsedCount(usageFloor);
        dailyOfferQuotaRepository.save(quota);

        log.error(
                "[OFFER QUOTA] Repaired undercount for bot {} on {} from {} to durable floor {}.",
                quota.getBot().getId(),
                quota.getUsageDate(),
                previous,
                usageFloor
        );
    }

    private int getDurableUsageFloor(
            Long botId,
            LocalDate usageDate
    ) {
        Set<UUID> durableRequestIds = new HashSet<>();

        for (RealActionAudit audit : getAuditedActions(botId, usageDate)) {
            if ((audit.getOutcome() == RealActionAuditOutcome.CONFIRMED
                    || audit.getOutcome() == RealActionAuditOutcome.AMBIGUOUS)
                    && audit.getRequestId() != null) {
                durableRequestIds.add(audit.getRequestId());
            }
        }

        for (DailyOfferQuotaReservation reservation : reservationRepository
                .findAllByBotIdAndUsageDateAndActiveTrue(botId, usageDate)) {
            durableRequestIds.add(reservation.getRequestId());
        }

        return durableRequestIds.size();
    }

    private List<RealActionAudit> getAuditedActions(
            Long botId,
            LocalDate usageDate
    ) {
        LocalDateTime dayStart = usageDate.atStartOfDay();
        LocalDateTime nextDayStart = usageDate.plusDays(1).atStartOfDay();

        return realActionAuditRepository
                .findAllByBotIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        botId,
                        dayStart,
                        nextDayStart
                );
    }

    private void validateReservationOwner(
            DailyOfferQuotaReservation reservation,
            Long botId
    ) {
        if (!Objects.equals(reservation.getBotId(), botId)) {
            throw new IllegalStateException(
                    "Quota reservation requestId " + reservation.getRequestId()
                            + " belongs to bot " + reservation.getBotId()
                            + ", not bot " + botId
            );
        }
    }

    private Bot getBot(Long botId) {
        return botRepository.findById(botId)
                .orElseThrow(() -> new BotNotFoundException(botId));
    }

    private int resolveDailyLimit(Bot bot) {
        if (bot.getConfiguration() == null
                || bot.getConfiguration().getDailyNegotiationBudget() == null) {
            return 0;
        }

        int configuredLimit = bot.getConfiguration().getDailyNegotiationBudget();
        if (configuredLimit <= 0) {
            return 0;
        }

        return Math.min(configuredLimit, HARD_MAX_DAILY_OFFER_LIMIT);
    }

    private void lockBotRow(Long botId) {
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

    private DailyOfferQuotaResponse createQuotaResponse(int limit, int used) {
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
