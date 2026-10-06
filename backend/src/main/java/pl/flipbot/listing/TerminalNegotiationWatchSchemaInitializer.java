package pl.flipbot.listing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(140)
@RequiredArgsConstructor
public class TerminalNegotiationWatchSchemaInitializer
        implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        /*
         * The local stabilized profile has Flyway disabled, therefore this
         * additive schema guard mirrors V48 so runtime upgrades stay safe.
         */
        jdbcTemplate.execute("""
                ALTER TABLE listing
                    ADD COLUMN IF NOT EXISTS last_terminal_watch_at TIMESTAMP
                """);

        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_listing_terminal_watch
                    ON listing (status, last_terminal_watch_at, decision_at)
                """);

        log.info(
                "Verified bounded terminal-negotiation watch schema."
        );
    }
}
