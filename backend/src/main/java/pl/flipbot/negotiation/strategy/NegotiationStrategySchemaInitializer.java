package pl.flipbot.negotiation.strategy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(120)
@RequiredArgsConstructor
public class NegotiationStrategySchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        /*
         * Flyway is disabled in the local runtime profile, therefore every
         * additive schema change used by the running application is mirrored
         * here as a self-healing upgrade.
         */
        jdbcTemplate.execute("""
                ALTER TABLE bot_configuration
                    ADD COLUMN IF NOT EXISTS negotiation_strategy_version INTEGER NOT NULL DEFAULT 1
                """);

        jdbcTemplate.execute("""
                ALTER TABLE bot_additional_target
                    ADD COLUMN IF NOT EXISTS negotiation_strategy_version INTEGER NOT NULL DEFAULT 1
                """);

        jdbcTemplate.execute("""
                ALTER TABLE listing
                    ADD COLUMN IF NOT EXISTS negotiation_strategy_version INTEGER,
                    ADD COLUMN IF NOT EXISTS negotiation_strategy_snapshot TEXT
                """);

        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_listing_negotiation_strategy_version
                    ON listing (negotiation_strategy_version)
                    WHERE negotiation_strategy_version IS NOT NULL
                """);

        log.info(
                "Verified versioned negotiation-strategy schema. Existing product definitions start at v1; active conversations are backfilled separately before the application becomes ready."
        );
    }
}
