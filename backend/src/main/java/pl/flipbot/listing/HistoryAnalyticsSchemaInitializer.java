package pl.flipbot.listing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Keeps the local development database compatible with history/analytics
 * columns while Flyway is intentionally disabled in application.yml.
 *
 * All operations are idempotent. The initializer does not change negotiation
 * state, quotas, sessions, scheduling or marketplace behaviour.
 */
@Slf4j
@Component
@Order(200)
@RequiredArgsConstructor
public class HistoryAnalyticsSchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute("""
                ALTER TABLE listing
                    ADD COLUMN IF NOT EXISTS history_outcome VARCHAR(40),
                    ADD COLUMN IF NOT EXISTS offer_assessment VARCHAR(40) NOT NULL DEFAULT 'UNASSESSED',
                    ADD COLUMN IF NOT EXISTS missed_opportunity_reason VARCHAR(40)
                """);

        int purchasedBackfill = jdbcTemplate.update("""
                UPDATE listing
                SET history_outcome = 'PURCHASED'
                WHERE status = 'PURCHASED'
                  AND history_outcome IS NULL
                """);

        int rejectedBackfill = jdbcTemplate.update("""
                UPDATE listing
                SET history_outcome = 'REJECTED'
                WHERE status = 'SKIPPED_BY_USER'
                  AND history_outcome IS NULL
                """);

        int decisionAtBackfill = jdbcTemplate.update("""
                UPDATE listing
                SET decision_at = COALESCE(
                        decision_at,
                        current_step_started_at,
                        last_fresh_discovery_at
                    )
                WHERE decision_at IS NULL
                  AND status IN (
                      'UNAVAILABLE',
                      'CONTACT_UNAVAILABLE',
                      'REJECTED',
                      'EXPIRED'
                  )
                """);

        /*
         * BotAdditionalTargetSchemaInitializer runs first and guarantees that
         * product_target_label / additional_target_id plus the optional-target
         * tables exist before this conservative legacy backfill executes.
         *
         * A bot may have been repurposed since an old listing was handled, so
         * the current target is accepted only when the stored listing title
         * independently contains the same model/search text. Otherwise the
         * old row deliberately stays unassigned instead of polluting model
         * analytics.
         */
        int additionalTargetLabels = jdbcTemplate.update("""
                UPDATE listing l
                SET product_target_label =
                        trim(t.brand)
                        || ' → '
                        || trim(
                            CASE
                                WHEN t.target_mode = 'SEARCH_QUERY' THEN t.search_query
                                ELSE t.model
                            END
                        )
                FROM bot_additional_target t
                WHERE l.product_target_label IS NULL
                  AND l.additional_target_id = t.id
                  AND t.brand IS NOT NULL
                  AND CASE
                          WHEN t.target_mode = 'SEARCH_QUERY' THEN t.search_query
                          ELSE t.model
                      END IS NOT NULL
                  AND position(
                          lower(
                              trim(
                                  CASE
                                      WHEN t.target_mode = 'SEARCH_QUERY' THEN t.search_query
                                      ELSE t.model
                                  END
                              )
                          )
                          in lower(l.title)
                      ) > 0
                """);

        int mainTargetLabels = jdbcTemplate.update("""
                UPDATE listing l
                SET product_target_label =
                        trim(c.brand)
                        || ' → '
                        || trim(
                            CASE
                                WHEN c.target_mode = 'SEARCH_QUERY' THEN c.search_query
                                ELSE c.model
                            END
                        )
                FROM bot_configuration c
                WHERE l.product_target_label IS NULL
                  AND l.additional_target_id IS NULL
                  AND l.bot_id = c.bot_id
                  AND c.brand IS NOT NULL
                  AND CASE
                          WHEN c.target_mode = 'SEARCH_QUERY' THEN c.search_query
                          ELSE c.model
                      END IS NOT NULL
                  AND position(
                          lower(
                              trim(
                                  CASE
                                      WHEN c.target_mode = 'SEARCH_QUERY' THEN c.search_query
                                      ELSE c.model
                                  END
                              )
                          )
                          in lower(l.title)
                      ) > 0
                """);

        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_listing_history_classification
                    ON listing (history_outcome, offer_assessment)
                """);

        log.info(
                "[HISTORY ANALYTICS] Verified local schema compatibility. Backfilled outcomes: purchased={}, rejected={}; decision timestamps={}; product labels: additional={}, main={}.",
                purchasedBackfill,
                rejectedBackfill,
                decisionAtBackfill,
                additionalTargetLabels,
                mainTargetLabels
        );
    }
}
