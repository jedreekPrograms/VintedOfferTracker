package pl.flipbot.negotiation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * One-time bridge for negotiation rows created under older runtime semantics.
 *
 * <p>Flyway is currently disabled in the normal runtime configuration, so
 * data-only migrations that merely live under db/migration are not sufficient
 * to repair an already-existing local database. This runner applies the narrow
 * legacy negotiation repair exactly once and records a database marker in the
 * same transaction.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LegacyNegotiationRecoveryRunner implements ApplicationRunner {

    private static final String REPAIR_KEY =
            "legacy-negotiation-recovery-2026-09-19-v1";

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    @Override
    public void run(ApplicationArguments args) {
        transactionTemplate.executeWithoutResult(
                status -> applyRepairOnce()
        );
    }

    private void applyRepairOnce() {
        jdbcTemplate.execute(
                """
                CREATE TABLE IF NOT EXISTS flipbot_data_repair (
                    repair_key VARCHAR(160) PRIMARY KEY,
                    applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """
        );

        int markerInserted = jdbcTemplate.update(
                """
                INSERT INTO flipbot_data_repair(repair_key)
                VALUES (?)
                ON CONFLICT (repair_key) DO NOTHING
                """,
                REPAIR_KEY
        );

        if (markerInserted == 0) {
            log.debug(
                    "[NEGOTIATION RECOVERY] Legacy negotiation repair '{}' was already applied.",
                    REPAIR_KEY
            );
            return;
        }

        int heuristicRows = reopenHeuristicallyClosedRows();
        int rejectedCapRows = reopenRejectedLegacyCapStops();
        int actionRequiredCapRows = reopenActionRequiredLegacyCapStops();

        log.warn(
                "[NEGOTIATION RECOVERY] Applied one-time legacy repair '{}'. Reopened heuristic rows={}, rejected cap-stop rows={}, action-required cap-stop rows={}. Reopened conversations will be re-inspected against the real Vinted state by the normal negotiation worker.",
                REPAIR_KEY,
                heuristicRows,
                rejectedCapRows,
                actionRequiredCapRows
        );
    }

    /**
     * Old runtime versions could close a still-live conversation after local
     * inactivity/contact heuristics. Current Playwright logic treats visible
     * Vinted state as authoritative, so give those historical rows one fresh
     * inspection. Explicit marketplace-unavailable rows use UNAVAILABLE and
     * are deliberately not touched.
     */
    private int reopenHeuristicallyClosedRows() {
        return jdbcTemplate.update(
                """
                UPDATE listing
                SET status = 'NEGOTIATING',
                    awaiting_seller_response = TRUE,
                    decision_at = NULL
                WHERE status IN ('EXPIRED', 'CONTACT_UNAVAILABLE')
                  AND conversation_id IS NOT NULL
                  AND BTRIM(conversation_id) <> ''
                  AND conversation_url IS NOT NULL
                  AND BTRIM(conversation_url) <> ''
                  AND current_step IS NOT NULL
                  AND current_step > 0
                """
        );
    }

    /**
     * Before the cap-plateau change, a rejected offer could become terminal as
     * soon as the calculated next adaptive step crossed maxAutomaticOffer.
     * REJECTED preserves the last own offer in listing.current_price, which
     * lets us prove that exact old boundary without guessing from Vinted text.
     */
    private int reopenRejectedLegacyCapStops() {
        return jdbcTemplate.update(
                """
                WITH rejected_cap_stops AS (
                    SELECT l.id
                    FROM listing l
                    JOIN bot_configuration bc
                      ON bc.bot_id = l.bot_id
                    JOIN negotiation_step current_step
                      ON current_step.configuration_id = bc.id
                     AND current_step.step_number = l.current_step
                    JOIN LATERAL (
                        SELECT ns.offer_price
                        FROM negotiation_step ns
                        WHERE ns.configuration_id = bc.id
                          AND ns.step_number > l.current_step
                        ORDER BY ns.step_number
                        LIMIT 1
                    ) next_step ON TRUE
                    WHERE l.status = 'REJECTED'
                      AND l.additional_target_id IS NULL
                      AND l.conversation_id IS NOT NULL
                      AND BTRIM(l.conversation_id) <> ''
                      AND l.conversation_url IS NOT NULL
                      AND BTRIM(l.conversation_url) <> ''
                      AND l.current_step IS NOT NULL
                      AND l.current_step > 0
                      AND l.current_price IS NOT NULL
                      AND l.current_price > 0
                      AND bc.auto_raise_offer_to_vinted_minimum = TRUE
                      AND bc.max_automatic_offer IS NOT NULL
                      AND bc.max_automatic_offer > 0
                      AND l.current_price <= bc.max_automatic_offer
                      AND current_step.offer_price IS NOT NULL
                      AND current_step.offer_price > 0
                      AND next_step.offer_price IS NOT NULL
                      AND next_step.offer_price > current_step.offer_price
                      AND (
                            CEIL(
                                (
                                    l.current_price
                                    * next_step.offer_price
                                    / current_step.offer_price
                                ) / 10
                            ) * 10
                          ) > bc.max_automatic_offer
                )
                UPDATE listing l
                SET status = 'NEGOTIATING',
                    awaiting_seller_response = FALSE,
                    decision_at = NULL
                WHERE l.id IN (SELECT id FROM rejected_cap_stops)
                """
        );
    }

    /**
     * Old cap-stop handling for a seller counteroffer used ACTION_REQUIRED.
     * That transition stores the seller's price in listing.current_price, so
     * the last confirmed real-action audit is used to recover the actual own
     * offer before reopening. Rows without such proof are left untouched.
     */
    private int reopenActionRequiredLegacyCapStops() {
        return jdbcTemplate.update(
                """
                WITH action_required_cap_stops AS (
                    SELECT
                        l.id,
                        last_action.offer_price AS own_offer_price
                    FROM listing l
                    JOIN bot_configuration bc
                      ON bc.bot_id = l.bot_id
                    JOIN negotiation_step current_step
                      ON current_step.configuration_id = bc.id
                     AND current_step.step_number = l.current_step
                    JOIN LATERAL (
                        SELECT ns.offer_price
                        FROM negotiation_step ns
                        WHERE ns.configuration_id = bc.id
                          AND ns.step_number > l.current_step
                        ORDER BY ns.step_number
                        LIMIT 1
                    ) next_step ON TRUE
                    JOIN LATERAL (
                        SELECT a.offer_price
                        FROM real_action_audit a
                        WHERE a.backend_listing_id = l.id
                          AND a.step_number = l.current_step
                          AND a.outcome = 'CONFIRMED'
                          AND a.offer_price IS NOT NULL
                          AND a.offer_price > 0
                        ORDER BY a.updated_at DESC, a.id DESC
                        LIMIT 1
                    ) last_action ON TRUE
                    WHERE l.status = 'ACTION_REQUIRED'
                      AND l.additional_target_id IS NULL
                      AND l.conversation_id IS NOT NULL
                      AND BTRIM(l.conversation_id) <> ''
                      AND l.conversation_url IS NOT NULL
                      AND BTRIM(l.conversation_url) <> ''
                      AND l.current_step IS NOT NULL
                      AND l.current_step > 0
                      AND l.current_price IS NOT NULL
                      AND bc.auto_raise_offer_to_vinted_minimum = TRUE
                      AND bc.max_automatic_offer IS NOT NULL
                      AND bc.max_automatic_offer > 0
                      -- A current legitimate adaptive acceptance cannot be
                      -- above the global cap. This distinguishes the legacy
                      -- cap-stop ACTION_REQUIRED rows from purchase candidates.
                      AND l.current_price > bc.max_automatic_offer
                      AND last_action.offer_price <= bc.max_automatic_offer
                      AND current_step.offer_price IS NOT NULL
                      AND current_step.offer_price > 0
                      AND next_step.offer_price IS NOT NULL
                      AND next_step.offer_price > current_step.offer_price
                      AND (
                            CEIL(
                                (
                                    last_action.offer_price
                                    * next_step.offer_price
                                    / current_step.offer_price
                                ) / 10
                            ) * 10
                          ) > bc.max_automatic_offer
                )
                UPDATE listing l
                SET status = 'NEGOTIATING',
                    current_price = recovery.own_offer_price,
                    awaiting_seller_response = FALSE,
                    decision_at = NULL
                FROM action_required_cap_stops recovery
                WHERE l.id = recovery.id
                """
        );
    }
}
