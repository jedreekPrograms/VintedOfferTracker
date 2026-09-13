package pl.flipbot.bot.configuration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BotAdditionalTargetSchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS bot_additional_target (
                    id BIGSERIAL PRIMARY KEY,
                    configuration_id BIGINT NOT NULL,
                    brand VARCHAR(255) NOT NULL,
                    target_mode VARCHAR(40) NOT NULL,
                    model VARCHAR(255),
                    search_query VARCHAR(255),
                    min_price NUMERIC(19, 2) NOT NULL,
                    max_price NUMERIC(19, 2) NOT NULL,
                    auto_raise_offer_to_vinted_minimum BOOLEAN NOT NULL DEFAULT FALSE,
                    max_automatic_offer NUMERIC(19, 2),
                    active BOOLEAN NOT NULL DEFAULT TRUE,
                    CONSTRAINT fk_bot_additional_target_configuration
                        FOREIGN KEY (configuration_id)
                        REFERENCES bot_configuration(id)
                        ON DELETE CASCADE
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS bot_additional_target_category_path (
                    target_id BIGINT NOT NULL,
                    path_index INTEGER NOT NULL,
                    category VARCHAR(255) NOT NULL,
                    PRIMARY KEY (target_id, path_index),
                    CONSTRAINT fk_bot_additional_target_category_path
                        FOREIGN KEY (target_id)
                        REFERENCES bot_additional_target(id)
                        ON DELETE CASCADE
                )
                """);

        jdbcTemplate.execute("""
                ALTER TABLE negotiation_step
                    ADD COLUMN IF NOT EXISTS additional_target_id BIGINT
                """);

        /*
         * Main-product negotiation steps keep configuration_id exactly as
         * before. Additional-product steps use additional_target_id instead,
         * therefore the legacy parent column must allow NULL. This is safe for
         * existing databases because no legacy row is rewritten.
         */
        jdbcTemplate.execute("""
                ALTER TABLE negotiation_step
                    ALTER COLUMN configuration_id DROP NOT NULL
                """);

        jdbcTemplate.execute("""
                ALTER TABLE listing
                    ADD COLUMN IF NOT EXISTS additional_target_id BIGINT
                """);

        ensureCascadeForeignKey(
                "negotiation_step",
                "fk_negotiation_step_additional_target",
                "additional_target_id"
        );
        ensureCascadeForeignKey(
                "listing",
                "fk_listing_additional_target",
                "additional_target_id"
        );

        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_bot_additional_target_configuration_active
                    ON bot_additional_target (configuration_id, active, id)
                """);

        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_listing_bot_additional_target_status
                    ON listing (bot_id, additional_target_id, status, id)
                """);

        log.info(
                "Verified optional additional-product schema. Existing bots keep their main configuration unchanged; additional targets are opt-in only."
        );
    }

    private void ensureCascadeForeignKey(
            String tableName,
            String constraintName,
            String columnName
    ) {
        jdbcTemplate.execute("""
                DO $$
                BEGIN
                    IF EXISTS (
                        SELECT 1
                        FROM pg_constraint
                        WHERE conname = '%s'
                          AND confdeltype <> 'c'
                    ) THEN
                        ALTER TABLE %s
                            DROP CONSTRAINT %s;
                    END IF;

                    IF NOT EXISTS (
                        SELECT 1
                        FROM pg_constraint
                        WHERE conname = '%s'
                    ) THEN
                        ALTER TABLE %s
                            ADD CONSTRAINT %s
                            FOREIGN KEY (%s)
                            REFERENCES bot_additional_target(id)
                            ON DELETE CASCADE;
                    END IF;
                END $$
                """.formatted(
                constraintName,
                tableName,
                constraintName,
                constraintName,
                tableName,
                constraintName,
                columnName
        ));
    }
}
