package pl.flipbot.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class LegacyCredentialEncryptionService implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncryptionConverter encryptionConverter;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT id, password
                FROM bot
                WHERE password IS NOT NULL
                  AND password <> ''
                ORDER BY id
                """
        );

        int migrated = 0;

        for (Map<String, Object> row : rows) {
            Object idValue = row.get("id");
            Object passwordValue = row.get("password");

            if (!(idValue instanceof Number number)
                    || !(passwordValue instanceof String databaseValue)) {
                throw new IllegalStateException(
                        "Unexpected bot credential row returned while migrating legacy passwords"
                );
            }

            if (encryptionConverter.isEncryptedDatabaseValue(databaseValue)) {
                continue;
            }

            String encrypted = encryptionConverter.convertToDatabaseColumn(databaseValue);
            int updated = jdbcTemplate.update(
                    """
                    UPDATE bot
                    SET password = ?
                    WHERE id = ?
                      AND password = ?
                    """,
                    encrypted,
                    number.longValue(),
                    databaseValue
            );

            if (updated == 1) {
                migrated++;
                continue;
            }

            // A second application instance may have migrated the same row
            // concurrently. Accept that race only if the current DB value is
            // now cryptographically valid for this application's key.
            String currentValue = jdbcTemplate.queryForObject(
                    "SELECT password FROM bot WHERE id = ?",
                    String.class,
                    number.longValue()
            );

            if (!encryptionConverter.isEncryptedDatabaseValue(currentValue)) {
                throw new IllegalStateException(
                        "Could not safely migrate legacy password for bot "
                                + number.longValue()
                );
            }
        }

        if (migrated > 0) {
            log.warn(
                    "[CREDENTIAL MIGRATION] Encrypted {} legacy bot password row(s) in-place. Plaintext values were not logged.",
                    migrated
            );
        }
    }
}
