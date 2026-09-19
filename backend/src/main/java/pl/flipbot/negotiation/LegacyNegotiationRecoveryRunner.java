package pl.flipbot.negotiation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

/**
 * Emergency corrective bridge for the 2026-09-19 legacy-negotiation recovery.
 *
 * <p>The first recovery version was too broad: it re-opened every historical
 * EXPIRED / CONTACT_UNAVAILABLE row that still had a conversation id. That can
 * create hundreds of artificial NEGOTIATING rows and must not be allowed to
 * drain through the normal next-step sender.</p>
 *
 * <p>This version never performs a broad re-open. If the old recovery marker is
 * present, it rolls back rows that can be tied to that exact recovery
 * transaction. It also catches the small subset already inspected by
 * Playwright after recovery, using the freshly-created formal-response
 * timestamp as evidence. The correction itself is one-shot and idempotent.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LegacyNegotiationRecoveryRunner implements ApplicationRunner {

    private static final String BROAD_RECOVERY_KEY =
            "legacy-negotiation-recovery-2026-09-19-v1";

    private static final String CORRECTIVE_ROLLBACK_KEY =
            "legacy-negotiation-recovery-2026-09-19-v2-rollback";

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    @Override
    public void run(ApplicationArguments args) {
        transactionTemplate.executeWithoutResult(
                status -> correctBroadRecoveryIfNeeded()
        );
    }

    private void correctBroadRecoveryIfNeeded() {
        jdbcTemplate.execute(
                """
                CREATE TABLE IF NOT EXISTS flipbot_data_repair (
                    repair_key VARCHAR(160) PRIMARY KEY,
                    applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """
        );

        RepairMarker broadRecovery =
                loadRepairMarker(BROAD_RECOVERY_KEY);

        if (broadRecovery == null) {
            log.info(
                    "[NEGOTIATION RECOVERY] Broad legacy recovery marker is absent. No historical conversations will be re-opened automatically."
            );
            return;
        }

        if (loadRepairMarker(CORRECTIVE_ROLLBACK_KEY) != null) {
            log.debug(
                    "[NEGOTIATION RECOVERY] Corrective rollback was already applied. No action required."
            );
            return;
        }

        /*
         * Rows not touched since the bad recovery still have the exact same
         * PostgreSQL xmin as the marker row because both were written in the
         * same transaction. This gives us a precise rollback for the bulk of
         * the accidental re-open without guessing by age, bot or title.
         */
        int exactTransactionRows = jdbcTemplate.update(
                """
                WITH repair_tx AS (
                    SELECT xmin AS recovery_xmin,
                           applied_at
                    FROM flipbot_data_repair
                    WHERE repair_key = ?
                )
                UPDATE listing l
                SET status = 'EXPIRED',
                    awaiting_seller_response = TRUE,
                    decision_at = COALESCE(
                        l.decision_at,
                        (SELECT applied_at FROM repair_tx)
                    )
                WHERE l.status = 'NEGOTIATING'
                  AND EXISTS (
                      SELECT 1
                      FROM repair_tx
                      WHERE l.xmin = repair_tx.recovery_xmin
                  )
                """,
                BROAD_RECOVERY_KEY
        );

        /*
         * A few rows may already have been inspected after recovery, which
         * changes xmin. The old recovery cleared decision_at; inspection of an
         * old rejected/counter-offer conversation then registers a brand-new
         * formal-response timestamp "now". Catch only that narrow signature:
         * old step, response timestamp created after the bad recovery, and no
         * real action sent after the recovery marker.
         *
         * The 24h age floor is intentionally conservative. It avoids touching
         * genuinely current conversations that happen to receive a fresh
         * seller response during the short correction window.
         */
        int alreadyInspectedRows = jdbcTemplate.update(
                """
                UPDATE listing l
                SET status = 'EXPIRED',
                    awaiting_seller_response = TRUE,
                    decision_at = COALESCE(l.decision_at, ?)
                WHERE l.status = 'NEGOTIATING'
                  AND l.current_step_started_at IS NOT NULL
                  AND l.current_step_started_at < ? - INTERVAL '24 hours'
                  AND l.formal_response_detected_at IS NOT NULL
                  AND l.formal_response_detected_at >= ?
                  AND NOT EXISTS (
                      SELECT 1
                      FROM real_action_audit a
                      WHERE a.backend_listing_id = l.id
                        AND a.created_at >= ?
                        AND a.outcome IN ('CONFIRMED', 'AMBIGUOUS')
                  )
                """,
                broadRecovery.appliedAt(),
                broadRecovery.appliedAt(),
                broadRecovery.appliedAt(),
                broadRecovery.appliedAt()
        );

        jdbcTemplate.update(
                """
                INSERT INTO flipbot_data_repair(repair_key)
                VALUES (?)
                ON CONFLICT (repair_key) DO NOTHING
                """,
                CORRECTIVE_ROLLBACK_KEY
        );

        log.error(
                "[NEGOTIATION RECOVERY] Corrected overly broad legacy recovery. Rolled back exact recovery-transaction rows={} and already-inspected old rows={}. Historical conversations are inactive again. No automatic historical re-open will run anymore.",
                exactTransactionRows,
                alreadyInspectedRows
        );
    }

    private RepairMarker loadRepairMarker(String repairKey) {
        return jdbcTemplate.query(
                """
                SELECT applied_at
                FROM flipbot_data_repair
                WHERE repair_key = ?
                """,
                resultSet -> resultSet.next()
                        ? new RepairMarker(
                                resultSet
                                        .getTimestamp("applied_at")
                                        .toLocalDateTime()
                        )
                        : null,
                repairKey
        );
    }

    private record RepairMarker(
            LocalDateTime appliedAt
    ) {
    }
}
