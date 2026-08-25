package pl.flipbot.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Component
@Converter
public class PasswordEncryptionConverter
        implements AttributeConverter<String, String> {

    private static final String PREFIX = "enc:v1:";
    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int AES_KEY_LENGTH_BYTES = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final SecretKey secretKey;

    public PasswordEncryptionConverter() {
        String encodedKey = System.getenv("FLIPBOT_ENCRYPTION_KEY");

        if (encodedKey == null || encodedKey.isBlank()) {
            throw new IllegalStateException(
                    "Environment variable FLIPBOT_ENCRYPTION_KEY is missing."
            );
        }

        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(encodedKey);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "FLIPBOT_ENCRYPTION_KEY must be valid Base64.",
                    exception
            );
        }

        if (keyBytes.length != AES_KEY_LENGTH_BYTES) {
            throw new IllegalStateException(
                    "FLIPBOT_ENCRYPTION_KEY must contain exactly 32 bytes."
            );
        }

        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    @Override
    public String convertToDatabaseColumn(String password) {
        if (password == null) {
            return null;
        }

        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            SECURE_RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    secretKey,
                    new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            );

            byte[] encryptedPassword = cipher.doFinal(
                    password.getBytes(StandardCharsets.UTF_8)
            );

            ByteBuffer buffer = ByteBuffer.allocate(
                    iv.length + encryptedPassword.length
            );
            buffer.put(iv);
            buffer.put(encryptedPassword);

            return PREFIX + Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Could not encrypt password.",
                    exception
            );
        }
    }

    @Override
    public String convertToEntityAttribute(String databaseValue) {
        if (databaseValue == null) {
            return null;
        }

        // Legacy non-prefixed plaintext can still be read for the duration of
        // the startup migration. Prefixed values are never treated as legacy:
        // if AES-GCM authentication fails, the configured key is wrong or the
        // stored ciphertext is corrupted and startup must fail closed.
        if (!databaseValue.startsWith(PREFIX)) {
            return databaseValue;
        }

        return decryptEncryptedValue(databaseValue);
    }

    /**
     * Returns false only for an unambiguously legacy, non-prefixed value.
     * A prefixed value must authenticate successfully with AES-GCM; otherwise
     * an exception is deliberately propagated so a wrong key can never cause
     * destructive double-encryption during the legacy migration.
     */
    public boolean isEncryptedDatabaseValue(String databaseValue) {
        if (databaseValue == null || !databaseValue.startsWith(PREFIX)) {
            return false;
        }

        decryptEncryptedValue(databaseValue);
        return true;
    }

    private String decryptEncryptedValue(String databaseValue) {
        try {
            String encodedPayload = databaseValue.substring(PREFIX.length());
            byte[] payload = Base64.getDecoder().decode(encodedPayload);

            if (payload.length <= IV_LENGTH_BYTES) {
                throw new IllegalStateException(
                        "Encrypted password has invalid format."
                );
            }

            byte[] iv = Arrays.copyOfRange(
                    payload,
                    0,
                    IV_LENGTH_BYTES
            );
            byte[] encryptedPassword = Arrays.copyOfRange(
                    payload,
                    IV_LENGTH_BYTES,
                    payload.length
            );

            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    secretKey,
                    new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            );

            byte[] decryptedPassword = cipher.doFinal(encryptedPassword);
            return new String(decryptedPassword, StandardCharsets.UTF_8);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Could not decrypt password. Check FLIPBOT_ENCRYPTION_KEY and stored credential integrity.",
                    exception
            );
        }
    }
}
