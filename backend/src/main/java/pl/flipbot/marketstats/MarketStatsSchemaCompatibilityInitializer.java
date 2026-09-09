package pl.flipbot.marketstats;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Applies the small, idempotent schema compatibility changes required by the
 * stable 2026-09-06 branch while Flyway remains intentionally disabled for the
 * historical development database.
 *
 * This is deliberately narrow: it does not replay the historical migration
 * chain. It only mirrors the compatibility statements introduced with the
 * market publication-time observer change.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketStatsSchemaCompatibilityInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        ensurePublishedAtColumn();
        normalizeStableRuntimeStatusConstraint();
    }

    private void ensurePublishedAtColumn() {
        jdbcTemplate.execute("""
                ALTER TABLE market_listing_observation
                    ADD COLUMN IF NOT EXISTS published_at TIMESTAMP
                """);

        jdbcTemplate.update("""
                UPDATE market_listing_observation
                SET published_at = first_seen_at
                WHERE published_at IS NULL
                """);

        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_market_listing_observation_model_effective_published_at
                    ON market_listing_observation (
                        model_id,
                        (COALESCE(published_at, first_seen_at))
                    )
                """);

        log.info(
                "[MARKET STATS] Verified local schema compatibility for market_listing_observation.published_at."
        );
    }

    private void normalizeStableRuntimeStatusConstraint() {
        jdbcTemplate.update("""
                UPDATE bot_runtime_state
                SET runtime_status = 'ERROR'
                WHERE runtime_status = 'CAPTCHA_REQUIRED'
                """);

        jdbcTemplate.execute("""
                ALTER TABLE bot_runtime_state
                    DROP CONSTRAINT IF EXISTS chk_bot_runtime_state_status
                """);

        jdbcTemplate.execute("""
                ALTER TABLE bot_runtime_state
                    ADD CONSTRAINT chk_bot_runtime_state_status
                    CHECK (runtime_status IN (
                        'IDLE',
                        'QUEUED',
                        'WORKING',
                        'COOLDOWN',
                        'ERROR'
                    ))
                """);

        log.info(
                "[RUNTIME] Verified stable runtime-status schema compatibility."
        );
    }
}
