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
                CREATE INDEX IF NOT EXISTS idx_market_listing_observation_model_published_at
                    ON market_listing_observation (model_id, published_at)
                    WHERE published_at IS NOT NULL
                """);

        log.info(
                "[MARKET STATS] Verified publication-time schema compatibility for market_listing_observation."
        );
    }
}
