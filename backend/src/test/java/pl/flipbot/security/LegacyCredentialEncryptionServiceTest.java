package pl.flipbot.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LegacyCredentialEncryptionServiceTest {

    private JdbcTemplate jdbcTemplate;
    private PasswordEncryptionConverter converter;
    private LegacyCredentialEncryptionService service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        converter = mock(PasswordEncryptionConverter.class);
        service = new LegacyCredentialEncryptionService(
                jdbcTemplate,
                converter
        );
    }

    @Test
    void migratesPlaintextPasswordWithoutLoggingOrReturningIt() {
        when(jdbcTemplate.queryForList(anyString())).thenReturn(
                List.of(Map.of("id", 7L, "password", "legacy-secret"))
        );
        when(converter.isEncryptedDatabaseValue("legacy-secret"))
                .thenReturn(false);
        when(converter.convertToDatabaseColumn("legacy-secret"))
                .thenReturn("enc:v1:ciphertext");
        when(jdbcTemplate.update(
                anyString(),
                eq("enc:v1:ciphertext"),
                eq(7L),
                eq("legacy-secret")
        )).thenReturn(1);

        assertDoesNotThrow(
                () -> service.run(mock(ApplicationArguments.class))
        );

        verify(jdbcTemplate).update(
                anyString(),
                eq("enc:v1:ciphertext"),
                eq(7L),
                eq("legacy-secret")
        );
    }

    @Test
    void leavesAlreadyEncryptedPasswordUntouched() {
        when(jdbcTemplate.queryForList(anyString())).thenReturn(
                List.of(Map.of("id", 8L, "password", "enc:v1:valid"))
        );
        when(converter.isEncryptedDatabaseValue("enc:v1:valid"))
                .thenReturn(true);

        assertDoesNotThrow(
                () -> service.run(mock(ApplicationArguments.class))
        );

        verify(converter, never()).convertToDatabaseColumn("enc:v1:valid");
        verify(jdbcTemplate, never()).update(
                anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void acceptsConcurrentMigrationOnlyWhenDatabaseNowContainsValidCiphertext() {
        when(jdbcTemplate.queryForList(anyString())).thenReturn(
                List.of(Map.of("id", 9L, "password", "legacy-secret"))
        );
        when(converter.isEncryptedDatabaseValue("legacy-secret"))
                .thenReturn(false);
        when(converter.convertToDatabaseColumn("legacy-secret"))
                .thenReturn("enc:v1:ours");
        when(jdbcTemplate.update(
                anyString(),
                eq("enc:v1:ours"),
                eq(9L),
                eq("legacy-secret")
        )).thenReturn(0);
        when(jdbcTemplate.queryForObject(
                anyString(),
                eq(String.class),
                eq(9L)
        )).thenReturn("enc:v1:other-instance");
        when(converter.isEncryptedDatabaseValue("enc:v1:other-instance"))
                .thenReturn(true);

        assertDoesNotThrow(
                () -> service.run(mock(ApplicationArguments.class))
        );
    }
}
