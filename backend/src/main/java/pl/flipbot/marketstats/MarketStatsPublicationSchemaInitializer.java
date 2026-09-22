package pl.flipbot.marketstats;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(300)
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
         * Local runtime keeps Flyway disabled. Mirror V44 here so databases
         * created before observer-price analytics can upgrade safely on startup
         * without changing the observer's existing counting/filtering logic.
         */
        jdbcTemplate.execute("""
                ALTER TABLE market_listing_observation
                    ADD COLUMN IF NOT EXISTS first_seen_price NUMERIC(38, 2),
                    ADD COLUMN IF NOT EXISTS latest_price NUMERIC(38, 2),
                    ADD COLUMN IF NOT EXISTS lowest_seen_price NUMERIC(38, 2),
                    ADD COLUMN IF NOT EXISTS highest_seen_price NUMERIC(38, 2)
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

        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_market_listing_observation_model_published_price
                    ON market_listing_observation (model_id, published_at)
                    WHERE latest_price IS NOT NULL OR first_seen_price IS NOT NULL
                """);

        log.info(
                "[MARKET STATS] Verified publication-time and observer-price schema compatibility plus publication-window coverage state; cleared {} synthetic first-seen timestamps for Vinted backfill.",
                clearedSynthetic
        );
    }
}