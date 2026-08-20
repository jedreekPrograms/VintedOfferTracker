package pl.flipbot.negotiation.quota;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.flipbot.bot.Bot;
import pl.flipbot.bot.BotRepository;
import pl.flipbot.exception.BotNotFoundException;
import pl.flipbot.negotiation.quota.dto.DailyOfferQuotaResponse;
import pl.flipbot.negotiation.quota.dto.OfferQuotaReservationResponse;

import java.time.LocalDate;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class DailyOfferQuotaService {

    private static final int HARD_MAX_DAILY_OFFER_LIMIT = 25;

    private static final ZoneId QUOTA_ZONE =
            ZoneId.of("Europe/Warsaw");

    private final DailyOfferQuotaRepository dailyOfferQuotaRepository;

    private final BotRepository botRepository;

    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public DailyOfferQuotaResponse getQuota(
            Long botId
    ) {
        Bot bot = getBot(botId);
        int dailyLimit = resolveDailyLimit(bot);
        LocalDate today = getToday();

        int used = dailyOfferQuotaRepository
                .findByBot_IdAndUsageDate(
                        botId,
                        today
                )
                .map(DailyOfferQuota::getUsedCount)
                .orElse(0);

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

        DailyOfferQuota quota = dailyOfferQuotaRepository
                .findByBot_IdAndUsageDate(
                        botId,
                        today
                )
                .orElseGet(
                        () -> DailyOfferQuota.builder()
                                .bot(bot)
                                .usageDate(today)
                                .usedCount(0)
                                .build()
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

        DailyOfferQuota quota = dailyOfferQuotaRepository
                .findByBot_IdAndUsageDate(
                        botId,
                        today
                )
                .orElse(null);

        if (quota == null) {
            return createQuotaResponse(
                    dailyLimit,
                    0
            );
        }

        if (quota.getUsedCount() > 0) {
            quota.setUsedCount(
                    quota.getUsedCount() - 1
            );
            dailyOfferQuotaRepository.save(quota);
        }

        return createQuotaResponse(
                dailyLimit,
                quota.getUsedCount()
        );
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
