package pl.flipbot.playwright.lab.fingerprint;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

/**
 * Encrypted cookie/local-storage persistence for controlled laboratory profiles.
 */
final class FingerprintLabSessionStore {

    private static final String SESSION_KEY_ENV =
            "FLIPBOT_SESSION_ENCRYPTION_KEY";
    private static final String FALLBACK_KEY_ENV =
            "FLIPBOT_ENCRYPTION_KEY";
    private static final String PREFIX = "flipbot-fingerprint-lab:v1:";
    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private FingerprintLabSessionStore() {}

    static Optional<String> load(FingerprintLabConfiguration configuration) {
        if (!configuration.persistentSession()) {
            return Optional.empty();
        }

        Path path = configuration.sessionStatePath();
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }

        try {
            String value = Files.readString(path, StandardCharsets.UTF_8);
            return Optional.of(decrypt(configuration.profileId(), value));
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Could not restore encrypted fingerprint-lab session for profile "
                            + configuration.profileId(),
                    exception
            );
        }
    }

    static void save(
            FingerprintLabConfiguration configuration,
            String storageState
    ) {
        if (!configuration.persistentSession()) {
            return;
        }
        if (storageState == null || storageState.isBlank()) {
            throw new IllegalArgumentException("storageState cannot be blank");
        }

        Path path = configuration.sessionStatePath();
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Files.writeString(
                    path,
                    encrypt(configuration.profileId(), storageState),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Could not save encrypted fingerprint-lab session for profile "
                            + configuration.profileId(),
                    exception
            );
        }
    }

    private static String encrypt(String profileId, String plaintext)
            throws Exception {
        byte[] iv = new byte[IV_BYTES];
        RANDOM.nextBytes(iv);

        Cipher cipher = Cipher.getInstance(CIPHER);
        cipher.init(
                Cipher.ENCRYPT_MODE,
                secretKey(),
                new GCMParameterSpec(TAG_BITS, iv)
        );
        cipher.updateAAD(aad(profileId));
        byte[] ciphertext = cipher.doFinal(
                plaintext.getBytes(StandardCharsets.UTF_8)
        );

        ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
        buffer.put(iv);
        buffer.put(ciphertext);
        return PREFIX + Base64.getEncoder().encodeToString(buffer.array());
    }

    private static String decrypt(String profileId, String value)
            throws Exception {
        if (value == null || !value.startsWith(PREFIX)) {
            throw new IllegalStateException("Invalid fingerprint-lab session format");
        }

        byte[] payload = Base64.getDecoder().decode(
                value.substring(PREFIX.length())
        );
        if (payload.length <= IV_BYTES) {
            throw new IllegalStateException("Invalid fingerprint-lab session payload");
        }

        byte[] iv = Arrays.copyOfRange(payload, 0, IV_BYTES);
        byte[] ciphertext = Arrays.copyOfRange(payload, IV_BYTES, payload.length);

        Cipher cipher = Cipher.getInstance(CIPHER);
        cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey(),
                new GCMParameterSpec(TAG_BITS, iv)
        );
        cipher.updateAAD(aad(profileId));
        return new String(
                cipher.doFinal(ciphertext),
                StandardCharsets.UTF_8
        );
    }

    private static SecretKey secretKey() {
        String encoded = System.getenv(SESSION_KEY_ENV);
        if (encoded == null || encoded.isBlank()) {
            encoded = System.getenv(FALLBACK_KEY_ENV);
        }
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalStateException(
                    "Persistent fingerprint-lab sessions require "
                            + SESSION_KEY_ENV
                            + " or "
                            + FALLBACK_KEY_ENV
            );
        }

        byte[] bytes = Base64.getDecoder().decode(encoded.trim());
        if (bytes.length != 32) {
            throw new IllegalStateException(
                    "Fingerprint-lab session encryption key must decode to exactly 32 bytes"
            );
        }
        return new SecretKeySpec(bytes, "AES");
    }

    private static byte[] aad(String profileId) {
        return ("flipbot-fingerprint-lab:profile:" + profileId)
                .getBytes(StandardCharsets.UTF_8);
    }
}
