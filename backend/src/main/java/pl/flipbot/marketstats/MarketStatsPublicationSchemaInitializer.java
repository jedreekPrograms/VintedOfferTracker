package pl.flipbot.marketstats;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarketStatsPublicationSchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute("""
                ALTER TABLE market_listing_observation
                    ADD COLUMN IF NOT EXISTS published_at TIMESTAMP
                """);

        jdbcTemplate.execute("""
                ALTER TABLE market_model_scan_state
                    ADD COLUMN IF NOT EXISTS publication_window_complete_at TIMESTAMP
                """);

        /*
         * Some earlier development builds temporarily copied first_seen_at into
         * published_at. That is observer time, not Vinted publication time.
         * Clear only that exact synthetic shape so the read-only observer can
         * backfill the real `Dodane ...` value from the item page.
         *
         * publication_window_complete_at deliberately remains NULL for legacy
         * scan states. A model must prove the real Vinted publication window at
         * least once before the calendar UI may call its statistics complete.
         */
        int clearedSynthetic = jdbcTemplate.update("""
                UPDATE market_listing_observation
                SET published_at = NULL
                WHERE published_at = first_seen_at
                """);

        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_market_listing_observation_model_published_at
                    ON market_listing_observation (model_id, published_at)
                    WHERE published_at IS NOT NULL
                """);

        log.info(
                "[MARKET STATS] Verified publication-time schema compatibility and publication-window coverage state; cleared {} synthetic first-seen timestamps for Vinted backfill.",
                clearedSynthetic
        );
    }
}
