package pl.flipbot.bot.activity;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.flipbot.bot.activity.dto.BotDailyActivityResponse;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/** Read-only dashboard snapshot. Real offer reservations still use DailyOfferQuotaService. */
@Service
@RequiredArgsConstructor
public class BotDailyActivitySnapshotService {

    private static final ZoneId ACTIVITY_ZONE = ZoneId.of("Europe/Warsaw");

    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public List<BotDailyActivityResponse> getToday() {
        return getForDate(LocalDate.now(ACTIVITY_ZONE));
    }

    List<BotDailyActivityResponse> getForDate(LocalDate date) {
        // One statement gives every bot the same database snapshot, without row locks,
        // loading entity graphs, or repairing quota rows during a dashboard refresh.
        // Keep ledger day attribution and request-id deduplication aligned with getQuota.
        return jdbcTemplate.query("""
                WITH today_audits AS (
                    SELECT a.bot_id, a.request_id, a.backend_listing_id, a.action_type, a.outcome,
                           r.bot_id AS reservation_bot_id, r.usage_date AS reservation_date
                    FROM real_action_audit a
                    LEFT JOIN daily_offer_quota_reservation r ON r.request_id = a.request_id
                    WHERE a.created_at >= ? AND a.created_at < ?
                ), started AS (
                    SELECT DISTINCT bot_id, backend_listing_id
                    FROM today_audits
                    WHERE outcome = 'CONFIRMED' AND action_type = 'FIRST_OFFER'
                ), activity AS (
                    SELECT a.bot_id,
                           COUNT(DISTINCT a.backend_listing_id) FILTER (
                               WHERE a.outcome = 'CONFIRMED' AND a.action_type = 'FIRST_OFFER'
                           ) AS new_negotiations,
                           COUNT(*) FILTER (
                               WHERE a.outcome = 'CONFIRMED' AND a.action_type = 'NEXT_STEP'
                                 AND s.backend_listing_id IS NOT NULL
                           ) AS new_next_steps,
                           COUNT(*) FILTER (
                               WHERE a.outcome = 'CONFIRMED' AND a.action_type = 'NEXT_STEP'
                                 AND s.backend_listing_id IS NULL
                           ) AS old_next_steps,
                           COUNT(*) FILTER (WHERE a.outcome = 'CONFIRMED') AS confirmed,
                           COUNT(*) FILTER (WHERE a.outcome = 'AMBIGUOUS') AS ambiguous,
                           COUNT(*) FILTER (
                               WHERE a.outcome IN ('CONFIRMED', 'AMBIGUOUS')
                                 AND a.reservation_bot_id <> a.bot_id
                           ) AS invalid_owners
                    FROM today_audits a
                    LEFT JOIN started s ON s.bot_id = a.bot_id
                                       AND s.backend_listing_id = a.backend_listing_id
                    GROUP BY a.bot_id
                ), durable_requests AS (
                    SELECT bot_id, request_id
                    FROM daily_offer_quota_reservation
                    WHERE usage_date = ? AND active = true
                    UNION
                    SELECT bot_id, request_id
                    FROM today_audits
                    WHERE outcome IN ('CONFIRMED', 'AMBIGUOUS') AND request_id IS NOT NULL
                      AND (reservation_bot_id IS NULL OR reservation_date = ?)
                ), durable_usage AS (
                    SELECT bot_id, COUNT(*) AS used FROM durable_requests GROUP BY bot_id
                ), active_negotiations AS (
                    SELECT bot_id, COUNT(*) AS active FROM listing
                    WHERE status = 'NEGOTIATING' GROUP BY bot_id
                )
                SELECT b.id AS bot_id,
                       LEAST(25, GREATEST(0, COALESCE(c.daily_negotiation_budget, 0))) AS daily_limit,
                       GREATEST(COALESCE(q.used_count, 0), COALESCE(u.used, 0)) AS used,
                       COALESCE(n.active, 0) AS active,
                       COALESCE(a.new_negotiations, 0) AS new_negotiations,
                       COALESCE(a.new_next_steps, 0) AS new_next_steps,
                       COALESCE(a.old_next_steps, 0) AS old_next_steps,
                       COALESCE(a.confirmed, 0) AS confirmed,
                       COALESCE(a.ambiguous, 0) AS ambiguous,
                       COALESCE(a.invalid_owners, 0) AS invalid_owners
                FROM bot b
                LEFT JOIN bot_configuration c ON c.bot_id = b.id
                LEFT JOIN daily_offer_quota q ON q.bot_id = b.id AND q.usage_date = ?
                LEFT JOIN durable_usage u ON u.bot_id = b.id
                LEFT JOIN active_negotiations n ON n.bot_id = b.id
                LEFT JOIN activity a ON a.bot_id = b.id
                ORDER BY b.id
                """, (rs, rowNum) -> {
            long botId = rs.getLong("bot_id");
            if (rs.getInt("invalid_owners") > 0) {
                throw new IllegalStateException("Quota reservation owner mismatch for bot " + botId);
            }
            int limit = rs.getInt("daily_limit");
            int used = rs.getInt("used");
            int confirmed = rs.getInt("confirmed");
            int ambiguous = rs.getInt("ambiguous");
            return new BotDailyActivityResponse(
                    botId, date, ACTIVITY_ZONE.getId(), limit, used, Math.max(limit - used, 0),
                    rs.getInt("active"), rs.getInt("new_negotiations"),
                    rs.getInt("new_next_steps"), rs.getInt("old_next_steps"),
                    confirmed, ambiguous, Math.max(used - confirmed - ambiguous, 0)
            );
        }, Timestamp.valueOf(date.atStartOfDay()), Timestamp.valueOf(date.plusDays(1).atStartOfDay()),
                Date.valueOf(date), Date.valueOf(date), Date.valueOf(date));
    }
}
