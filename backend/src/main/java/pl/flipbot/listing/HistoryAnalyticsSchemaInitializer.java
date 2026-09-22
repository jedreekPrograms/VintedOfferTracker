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

        /*
         * Some local databases already contained an earlier experimental
         * history-classification shape. Normalize those values before any JPA
         * query can hydrate Listing, otherwise Enum.valueOf would fail and make
         * Dashboard/History/Analytics all return HTTP 500 at once.
         */
        int legacyPurchased = jdbcTemplate.update("""
                UPDATE listing
                SET history_outcome = 'PURCHASED'
                WHERE upper(trim(history_outcome)) IN (
                    'PURCHASED_BY_ME',
                    'BOUGHT_BY_ME',
                    'BOUGHT'
                )
                """);

        int legacyRejected = jdbcTemplate.update("""
                UPDATE listing
                SET history_outcome = 'REJECTED'
                WHERE upper(trim(history_outcome)) IN (
                    'REJECTED_BY_ME',
                    'SKIPPED_BY_ME'
                )
                """);

        int unknownOutcomes = jdbcTemplate.update("""
                UPDATE listing
                SET history_outcome = CASE
                    WHEN status = 'PURCHASED' THEN 'PURCHASED'
                    WHEN status = 'SKIPPED_BY_USER' THEN 'REJECTED'
                    ELSE 'UNCLASSIFIED'
                END
                WHERE history_outcome IS NOT NULL
                  AND upper(trim(history_outcome)) NOT IN (
                      'UNCLASSIFIED',
                      'PURCHASED',
                      'REJECTED',
                      'MISSED_OPPORTUNITY'
                  )
                """);

        int unknownAssessments = jdbcTemplate.update("""
                UPDATE listing
                SET offer_assessment = 'UNASSESSED'
                WHERE offer_assessment IS NULL
                   OR upper(trim(offer_assessment)) NOT IN (
                       'UNASSESSED',
                       'LEGIT',
                       'SCAM'
                   )
                """);

        int unknownMissedReasons = jdbcTemplate.update("""
                UPDATE listing
                SET missed_opportunity_reason = NULL
                WHERE missed_opportunity_reason IS NOT NULL
                  AND upper(trim(missed_opportunity_reason)) NOT IN (
                      'SOLD_BEFORE_PURCHASE',
                      'NO_FUNDS',
                      'TOO_SLOW',
                      'OTHER'
                  )
                """);

        jdbcTemplate.execute("""
                ALTER TABLE listing
                    ALTER COLUMN offer_assessment SET DEFAULT 'UNASSESSED'
                """);

        jdbcTemplate.execute("""
                ALTER TABLE listing
                    ALTER COLUMN offer_assessment SET NOT NULL
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
                "[HISTORY ANALYTICS] Verified local schema compatibility. Legacy normalized: purchased={}, rejected={}, unknown outcomes={}, unknown assessments={}, unknown missed reasons={}. Backfilled outcomes: purchased={}, rejected={}; decision timestamps={}; product labels: additional={}, main={}.",
                legacyPurchased,
                legacyRejected,
                unknownOutcomes,
                unknownAssessments,
                unknownMissedReasons,
                purchasedBackfill,
                rejectedBackfill,
                decisionAtBackfill,
                additionalTargetLabels,
                mainTargetLabels
        );
    }
}