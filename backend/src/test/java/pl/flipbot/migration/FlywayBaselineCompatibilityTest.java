package pl.flipbot.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FlywayBaselineCompatibilityTest {

    @Test
    void addingSameVersionBaselineDoesNotTouchExistingVersionedHistory() throws Exception {
        String jdbcUrl = environment(
                "SPRING_DATASOURCE_URL",
                "jdbc:postgresql://127.0.0.1:5433/flipbot"
        );
        String username = environment("SPRING_DATASOURCE_USERNAME", "postgres");
        String password = environment("SPRING_DATASOURCE_PASSWORD", "postgres");
        String schema = "flyway_baseline_compat_"
                + UUID.randomUUID().toString().replace("-", "");
        Path migrations = Files.createTempDirectory("flipbot-flyway-baseline-");

        Files.writeString(
                migrations.resolve("V1__create_marker.sql"),
                "CREATE TABLE marker (id INTEGER PRIMARY KEY);\n"
        );
        Files.writeString(
                migrations.resolve("V2__extend_marker.sql"),
                "ALTER TABLE marker ADD COLUMN payload VARCHAR(100);\n"
        );

        try {
            Flyway beforeBaseline = flyway(
                    jdbcUrl,
                    username,
                    password,
                    schema,
                    migrations
            );

            MigrateResult initialMigration = beforeBaseline.migrate();
            assertEquals(2, initialMigration.migrationsExecuted);

            // This models the FlipBot Stage 3 rollout: an installation already
            // has V1..V35 in flyway_schema_history and a new B35 file appears
            // in the application. Flyway must match the applied V migrations
            // and ignore the same-version baseline instead of executing it.
            Files.writeString(
                    migrations.resolve("B2__current_schema_baseline.sql"),
                    "CREATE TABLE baseline_only_marker (id INTEGER PRIMARY KEY);\n"
            );

            Flyway afterBaseline = flyway(
                    jdbcUrl,
                    username,
                    password,
                    schema,
                    migrations
            );

            assertDoesNotThrow(afterBaseline::validate);

            MigrateResult rolloutMigration = afterBaseline.migrate();
            assertEquals(0, rolloutMigration.migrationsExecuted);

            try (Connection connection = DriverManager.getConnection(
                    jdbcUrl,
                    username,
                    password
            ); Statement statement = connection.createStatement()) {
                assertEquals(
                        2,
                        scalarCount(
                                statement,
                                "SELECT COUNT(*) FROM " + schema
                                        + ".flyway_schema_history WHERE success = TRUE"
                        )
                );
                assertEquals(
                        0,
                        scalarCount(
                                statement,
                                "SELECT COUNT(*) FROM " + schema
                                        + ".flyway_schema_history WHERE type = 'SQL_BASELINE'"
                        )
                );
                assertEquals(
                        0,
                        scalarCount(
                                statement,
                                "SELECT CASE WHEN to_regclass('" + schema
                                        + ".baseline_only_marker') IS NULL THEN 0 ELSE 1 END"
                        )
                );
            }
        } finally {
            try (Connection connection = DriverManager.getConnection(
                    jdbcUrl,
                    username,
                    password
            ); Statement statement = connection.createStatement()) {
                statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
        }
    }

    private Flyway flyway(
            String jdbcUrl,
            String username,
            String password,
            String schema,
            Path migrations
    ) {
        return Flyway.configure()
                .dataSource(jdbcUrl, username, password)
                .schemas(schema)
                .defaultSchema(schema)
                .createSchemas(true)
                .locations("filesystem:" + migrations.toAbsolutePath())
                .load();
    }

    private long scalarCount(
            Statement statement,
            String sql
    ) throws Exception {
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private String environment(
            String key,
            String fallback
    ) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
