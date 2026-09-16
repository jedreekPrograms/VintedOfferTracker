package pl.flipbot.bot.activity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import pl.flipbot.bot.activity.dto.BotDailyActivityResponse;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class BotDailyActivitySnapshotServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 16);

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private BotDailyActivitySnapshotService service;
    @Autowired
    private BotDailyActivityService individualService;

    @Test
    void aggregatesBotsWithoutRepairingQuotaAndDeduplicatesLedgerAndAuditRequests() {
        long first = bot(50);
        long second = bot(2);
        long empty = bot(null);
        listing(first, "NEGOTIATING");
        listing(first, "NEGOTIATING");
        listing(second, "NEGOTIATING");

        UUID confirmedRequest = audit(first, 101, "FIRST_OFFER", "CONFIRMED", DAY.atStartOfDay());
        reservation(first, confirmedRequest, DAY, true);
        audit(first, 101, "FIRST_OFFER", "CONFIRMED", DAY.atTime(9, 0));
        audit(first, 101, "NEXT_STEP", "CONFIRMED", DAY.atTime(10, 0));
        audit(first, 102, "NEXT_STEP", "CONFIRMED", DAY.atTime(11, 0));
        UUID ambiguous = audit(first, 103, "FIRST_OFFER", "AMBIGUOUS", DAY.atTime(12, 0));
        // A durable audit remains counted even if an old ledger entry was released.
        reservation(first, ambiguous, DAY, false);
        reservation(first, UUID.randomUUID(), DAY, true);
        reservation(first, UUID.randomUUID(), DAY, false);
        quota(first, 1);
        quota(second, 4);

        var rows = service.getForDate(DAY);
        assertEquals(new BotDailyActivityResponse(first, DAY, "Europe/Warsaw",
                25, 6, 19, 2, 1, 1, 1, 4, 1, 1), find(rows, first));
        assertEquals(new BotDailyActivityResponse(second, DAY, "Europe/Warsaw",
                2, 4, 0, 1, 0, 0, 0, 0, 0, 4), find(rows, second));
        assertEquals(new BotDailyActivityResponse(empty, DAY, "Europe/Warsaw",
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0), find(rows, empty));
        assertEquals(1, jdbc.queryForObject(
                "SELECT used_count FROM daily_offer_quota WHERE bot_id = ?", Integer.class, first));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM daily_offer_quota WHERE bot_id = ?", Integer.class, empty));
    }

    @Test
    void respectsMidnightBoundariesAndOriginalReservationDayWithoutCreatingQuotaRows() {
        long bot = bot(25);
        UUID beforeMidnight = audit(bot, 201, "FIRST_OFFER", "CONFIRMED", DAY.atStartOfDay());
        reservation(bot, beforeMidnight, DAY.minusDays(1), true);
        audit(bot, 201, "NEXT_STEP", "CONFIRMED", DAY.atTime(1, 0));
        audit(bot, 202, "FIRST_OFFER", "CONFIRMED", DAY.atStartOfDay().minusNanos(1000));
        audit(bot, 203, "FIRST_OFFER", "CONFIRMED", DAY.plusDays(1).atStartOfDay());

        assertEquals(new BotDailyActivityResponse(bot, DAY, "Europe/Warsaw",
                25, 1, 24, 0, 1, 1, 0, 2, 0, 0), find(service.getForDate(DAY), bot));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM daily_offer_quota WHERE bot_id = ?", Integer.class, bot));
    }

    @Test
    void matchesTheExistingSingleBotResponseForCurrentDay() {
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Europe/Warsaw"));
        long bot = bot(12);
        listing(bot, "NEGOTIATING");
        UUID request = audit(bot, 301, "FIRST_OFFER", "CONFIRMED", today.atStartOfDay());
        reservation(bot, request, today, true);
        audit(bot, 301, "NEXT_STEP", "AMBIGUOUS", today.atTime(1, 0));
        reservation(bot, UUID.randomUUID(), today, true);

        var snapshot = find(service.getToday(), bot);
        assertEquals(individualService.getToday(bot), snapshot);
    }

    @Test
    void clampsNegativeBudgetAndRejectsMismatchedReservationOwners() {
        long bot = bot(-1);
        long other = bot(25);
        assertEquals(0, find(service.getForDate(DAY), bot).dailyLimit());
        UUID request = audit(bot, 401, "FIRST_OFFER", "CONFIRMED", DAY.atStartOfDay());
        reservation(other, request, DAY, true);
        assertThrows(IllegalStateException.class, () -> service.getForDate(DAY));
    }

    private long bot(Integer limit) {
        long id = jdbc.queryForObject("""
                INSERT INTO bot (name, status, market_stats_observer)
                VALUES ('snapshot-test', 'STOPPED', false) RETURNING id
                """, Long.class);
        if (limit != null) {
            jdbc.update("INSERT INTO bot_configuration (bot_id, daily_negotiation_budget) VALUES (?, ?)",
                    id, limit);
        }
        return id;
    }

    private void listing(long bot, String status) {
        jdbc.update("""
                INSERT INTO listing (bot_id, listing_id, title, url, original_price, current_price,
                                     current_step, awaiting_seller_response, status, history_hidden)
                VALUES (?, ?, 'snapshot-test', 'https://example.invalid/item', 100, 90, 1, true, ?, false)
                """, bot, UUID.randomUUID().toString(), status);
    }

    private UUID audit(long bot, long listing, String action, String outcome, LocalDateTime time) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO real_action_audit (request_id, bot_id, backend_listing_id, marketplace_listing_id,
                                              action_type, step_number, outcome, message_status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 1, ?, 'UNKNOWN', ?, ?)
                """, id, bot, listing, Long.toString(listing), action, outcome,
                Timestamp.valueOf(time), Timestamp.valueOf(time));
        return id;
    }

    private void reservation(long bot, UUID request, LocalDate day, boolean active) {
        jdbc.update("""
                INSERT INTO daily_offer_quota_reservation (request_id, bot_id, usage_date, active, created_at)
                VALUES (?, ?, ?, ?, ?)
                """, request, bot, Date.valueOf(day), active, Timestamp.valueOf(day.atStartOfDay()));
    }

    private void quota(long bot, int used) {
        jdbc.update("INSERT INTO daily_offer_quota (bot_id, usage_date, used_count) VALUES (?, ?, ?)",
                bot, Date.valueOf(DAY), used);
    }

    private BotDailyActivityResponse find(java.util.List<BotDailyActivityResponse> rows, long bot) {
        return rows.stream().filter(row -> row.botId() == bot).findFirst().orElseThrow();
    }
}
