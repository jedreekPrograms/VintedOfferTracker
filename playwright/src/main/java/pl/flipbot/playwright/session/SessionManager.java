package pl.flipbot.playwright.session;

import com.microsoft.playwright.BrowserContext;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class SessionManager {

    private static final Path DEFAULT_SESSION_DIRECTORY =
            Path.of("sessions");

    private static final String SESSION_KEY_ENV =
            "FLIPBOT_SESSION_ENCRYPTION_KEY";
    private static final String FALLBACK_KEY_ENV =
            "FLIPBOT_ENCRYPTION_KEY";

    private static final String ENCRYPTED_PREFIX =
            "flipbot-session:v1:";
    private static final String CIPHER_ALGORITHM =
            "AES/GCM/NoPadding";
    private static final String AAD_PREFIX =
            "flipbot-session:bot:";

    private static final int AES_KEY_LENGTH_BYTES = 32;
    private static final int IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private static final SecureRandom SECURE_RANDOM =
            new SecureRandom();

    private static final Pattern SESSION_FILE_PATTERN =
            Pattern.compile("^bot-(\\d+)\\.(?:state\\.enc|json)$");

    private static final Set<PosixFilePermission> DIRECTORY_PERMISSIONS =
            Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE
            );

    private static final Set<PosixFilePermission> FILE_PERMISSIONS =
            Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE
            );

    private final Path sessionDirectory;
    private final String encodedKeyOverride;

    private volatile SecretKey cachedSecretKey;

    public SessionManager() {
        this(DEFAULT_SESSION_DIRECTORY, null);
    }

    SessionManager(
            Path sessionDirectory,
            String encodedKeyOverride
    ) {
        if (sessionDirectory == null) {
            throw new IllegalArgumentException(
                    "Session directory is required."
            );
        }

        this.sessionDirectory = sessionDirectory;
        this.encodedKeyOverride = encodedKeyOverride;

        try {
            Files.createDirectories(sessionDirectory);
            applyOwnerOnlyPermissions(
                    sessionDirectory,
                    DIRECTORY_PERMISSIONS
            );
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not create secure Playwright session directory: "
                            + sessionDirectory,
                    exception
            );
        }
    }

    public boolean sessionExists(Long botId) {
        validateBotId(botId);

        return Files.exists(encryptedSessionFile(botId))
                || Files.exists(legacySessionFile(botId));
    }

    /**
     * Compatibility contract used by BotContext. The returned Path is only a
     * logical reference to stored session material. BrowserManager must resolve
     * it through readStorageStateFromReference() and pass the resulting JSON to
     * Playwright in memory; the path itself is never given to Playwright.
     */
    public Path sessionFile(Long botId) {
        validateBotId(botId);

        Path encrypted = encryptedSessionFile(botId);
        if (Files.exists(encrypted)) {
            return encrypted;
        }

        return legacySessionFile(botId);
    }

    /**
     * Resolves an encrypted/legacy session reference into plaintext JSON in
     * memory. Legacy .json state is migrated to encrypted storage before this
     * method returns when a valid encryption key is available.
     */
    public static String readStorageStateFromReference(Path sessionReference) {
        if (sessionReference == null
                || sessionReference.getFileName() == null) {
            throw new IllegalArgumentException(
                    "Session reference is required."
            );
        }

        Matcher matcher = SESSION_FILE_PATTERN.matcher(
                sessionReference.getFileName().toString()
        );

        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "Unsupported Playwright session reference: "
                            + sessionReference
            );
        }

        Long botId;
        try {
            botId = Long.valueOf(matcher.group(1));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "Invalid bot ID in Playwright session reference: "
                            + sessionReference,
                    exception
            );
        }

        Path parent = sessionReference.getParent();
        if (parent == null) {
            parent = Path.of(".");
        }

        return new SessionManager(parent, null)
                .loadSessionState(botId)
                .orElseThrow(() -> new IllegalStateException(
                        "Playwright session disappeared before it could be restored for bot "
                                + botId
                                + "."
                ));
    }

    /**
     * Loads a storage-state JSON document into memory. Persistent state is
     * encrypted with AES-256-GCM and authenticated against the owning bot ID.
     *
     * A legacy plaintext bot-{id}.json is migrated only after the encrypted
     * replacement has been durably written and authenticated by reading it back.
     */
    public Optional<String> loadSessionState(Long botId) {
        validateBotId(botId);

        Path encryptedPath = encryptedSessionFile(botId);

        if (Files.exists(encryptedPath)) {
            String encryptedValue = readFile(encryptedPath);
            String storageState = decrypt(botId, encryptedValue);

            // An interrupted historical migration may have left both files.
            // Delete plaintext only after the encrypted copy authenticated.
            deleteLegacyAfterEncryptedStateIsVerified(botId);
            return Optional.of(storageState);
        }

        Path legacyPath = legacySessionFile(botId);
        if (!Files.exists(legacyPath)) {
            return Optional.empty();
        }

        String legacyStorageState = readFile(legacyPath);
        saveSessionState(botId, legacyStorageState);

        log.warn(
                "[SESSION] Migrated legacy plaintext Playwright session for bot {} "
                        + "to authenticated AES-GCM storage and removed the plaintext file.",
                botId
        );

        return Optional.of(legacyStorageState);
    }

    public void saveSession(
            Long botId,
            BrowserContext context
    ) {
        if (context == null) {
            throw new IllegalArgumentException(
                    "Browser context is required to save a session."
            );
        }

        /*
         * No Path is passed to Playwright here. storageState() returns the JSON
         * in memory, so cookies/localStorage are encrypted before any persistent
         * write occurs.
         */
        saveSessionState(
                botId,
                context.storageState()
        );
    }

    void saveSessionState(
            Long botId,
            String storageState
    ) {
        validateBotId(botId);

        if (storageState == null || storageState.isBlank()) {
            throw new IllegalArgumentException(
                    "Playwright storage state cannot be blank."
            );
        }

        Path target = encryptedSessionFile(botId);

        /*
         * Never overwrite existing encrypted material unless the currently
         * configured key can authenticate it first. This is essential during
         * upgrades: a worker started with a missing/wrong key may fall back to a
         * clean login, but it must not destroy the previous valid session by
         * saving fresh state under the accidental key.
         */
        if (Files.exists(target)) {
            decrypt(botId, readFile(target));
        }

        String encryptedValue = encrypt(botId, storageState);
        Path temporary = null;

        try {
            Files.createDirectories(sessionDirectory);
            applyOwnerOnlyPermissions(
                    sessionDirectory,
                    DIRECTORY_PERMISSIONS
            );

            temporary = Files.createTempFile(
                    sessionDirectory,
                    ".bot-" + botId + "-",
                    ".state.enc.tmp"
            );

            Files.writeString(
                    temporary,
                    encryptedValue,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
            applyOwnerOnlyPermissions(
                    temporary,
                    FILE_PERMISSIONS
            );

            try {
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }

            temporary = null;
            applyOwnerOnlyPermissions(
                    target,
                    FILE_PERMISSIONS
            );

            /*
             * Read back and authenticate the final file before deleting legacy
             * plaintext. This also proves that a non-atomic fallback move did
             * not leave a truncated/corrupted encrypted state behind.
             */
            String verifiedStorageState = decrypt(
                    botId,
                    readFile(target)
            );

            if (!storageState.equals(verifiedStorageState)) {
                throw new IllegalStateException(
                        "Encrypted Playwright session verification failed for bot "
                                + botId
                                + ". Legacy plaintext was left untouched."
                );
            }

            deleteLegacyAfterEncryptedStateIsVerified(botId);

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not persist encrypted Playwright session for bot "
                            + botId
                            + ".",
                    exception
            );
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException exception) {
                    log.warn(
                            "[SESSION] Could not remove encrypted temporary session file for bot {}.",
                            botId,
                            exception
                    );
                }
            }
        }
    }

    public void invalidateSession(Long botId) {
        validateBotId(botId);

        boolean removedEncrypted = deleteIfExists(
                encryptedSessionFile(botId),
                botId,
                "encrypted"
        );
        boolean removedLegacy = deleteIfExists(
                legacySessionFile(botId),
                botId,
                "legacy plaintext"
        );

        if (removedEncrypted || removedLegacy) {
            log.warn(
                    "[SESSION] Removed stored session material for bot {}.",
                    botId
            );
        }
    }

    /**
     * Removes encrypted or legacy session files whose bot no longer exists in
     * the backend. Stopped bots are intentionally retained: callers must pass
     * every existing bot ID, not only RUNNING ones.
     */
    public int deleteSessionsForMissingBots(Set<Long> existingBotIds) {
        if (existingBotIds == null) {
            throw new IllegalArgumentException(
                    "Existing bot IDs are required for session cleanup."
            );
        }

        int removed = 0;

        try (var files = Files.list(sessionDirectory)) {
            for (Path path : files.toList()) {
                Matcher matcher = SESSION_FILE_PATTERN.matcher(
                        path.getFileName().toString()
                );

                if (!matcher.matches()) {
                    continue;
                }

                Long botId;
                try {
                    botId = Long.valueOf(matcher.group(1));
                } catch (NumberFormatException exception) {
                    continue;
                }

                if (existingBotIds.contains(botId)) {
                    continue;
                }

                if (deleteIfExists(path, botId, "orphaned")) {
                    removed++;
                }
            }
        } catch (IOException exception) {
            log.warn(
                    "[SESSION] Could not scan session directory for orphaned bot state.",
                    exception
            );
        }

        if (removed > 0) {
            log.warn(
                    "[SESSION] Removed {} orphaned session file(s) for bot IDs that no longer exist.",
                    removed
            );
        }

        return removed;
    }

    Path encryptedSessionFile(Long botId) {
        validateBotId(botId);
        return sessionDirectory.resolve(
                "bot-" + botId + ".state.enc"
        );
    }

    Path legacySessionFile(Long botId) {
        validateBotId(botId);
        return sessionDirectory.resolve(
                "bot-" + botId + ".json"
        );
    }

    private String encrypt(
            Long botId,
            String plaintext
    ) {
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            SECURE_RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    secretKey(),
                    new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            );
            cipher.updateAAD(aad(botId));

            byte[] ciphertext = cipher.doFinal(
                    plaintext.getBytes(StandardCharsets.UTF_8)
            );

            ByteBuffer payload = ByteBuffer.allocate(
                    iv.length + ciphertext.length
            );
            payload.put(iv);
            payload.put(ciphertext);

            return ENCRYPTED_PREFIX
                    + Base64.getEncoder().encodeToString(payload.array());

        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Could not encrypt Playwright session for bot "
                            + botId
                            + ".",
                    exception
            );
        }
    }

    private String decrypt(
            Long botId,
            String encryptedValue
    ) {
        if (encryptedValue == null
                || !encryptedValue.startsWith(ENCRYPTED_PREFIX)) {
            throw new IllegalStateException(
                    "Encrypted Playwright session for bot "
                            + botId
                            + " has an invalid format."
            );
        }

        try {
            byte[] payload = Base64.getDecoder().decode(
                    encryptedValue.substring(ENCRYPTED_PREFIX.length())
            );

            if (payload.length <= IV_LENGTH_BYTES) {
                throw new IllegalStateException(
                        "Encrypted Playwright session for bot "
                                + botId
                                + " has an invalid payload."
                );
            }

            byte[] iv = Arrays.copyOfRange(
                    payload,
                    0,
                    IV_LENGTH_BYTES
            );
            byte[] ciphertext = Arrays.copyOfRange(
                    payload,
                    IV_LENGTH_BYTES,
                    payload.length
            );

            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    secretKey(),
                    new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            );
            cipher.updateAAD(aad(botId));

            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);

        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Could not decrypt Playwright session for bot "
                            + botId
                            + ". Check "
                            + SESSION_KEY_ENV
                            + "/"
                            + FALLBACK_KEY_ENV
                            + " and session-file integrity. The encrypted file was left untouched.",
                    exception
            );
        }
    }

    private SecretKey secretKey() {
        SecretKey existing = cachedSecretKey;
        if (existing != null) {
            return existing;
        }

        synchronized (this) {
            if (cachedSecretKey != null) {
                return cachedSecretKey;
            }

            String encodedKey = encodedKeyOverride;

            if (encodedKey == null || encodedKey.isBlank()) {
                encodedKey = System.getenv(SESSION_KEY_ENV);
            }

            if (encodedKey == null || encodedKey.isBlank()) {
                encodedKey = System.getenv(FALLBACK_KEY_ENV);
            }

            if (encodedKey == null || encodedKey.isBlank()) {
                throw new IllegalStateException(
                        "Authenticated Playwright session persistence requires "
                                + SESSION_KEY_ENV
                                + " or fallback "
                                + FALLBACK_KEY_ENV
                                + "."
                );
            }

            byte[] keyBytes;
            try {
                keyBytes = Base64.getDecoder().decode(encodedKey);
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException(
                        "Playwright session encryption key must be valid Base64.",
                        exception
                );
            }

            if (keyBytes.length != AES_KEY_LENGTH_BYTES) {
                throw new IllegalStateException(
                        "Playwright session encryption key must contain exactly 32 bytes."
                );
            }

            cachedSecretKey = new SecretKeySpec(keyBytes, "AES");
            return cachedSecretKey;
        }
    }

    private byte[] aad(Long botId) {
        return (AAD_PREFIX + botId)
                .getBytes(StandardCharsets.UTF_8);
    }

    private String readFile(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not read Playwright session file: " + path,
                    exception
            );
        }
    }

    private void deleteLegacyAfterEncryptedStateIsVerified(Long botId) {
        deleteIfExists(
                legacySessionFile(botId),
                botId,
                "legacy plaintext"
        );
    }

    private boolean deleteIfExists(
            Path path,
            Long botId,
            String kind
    ) {
        try {
            return Files.deleteIfExists(path);
        } catch (IOException exception) {
            log.warn(
                    "[SESSION] Could not delete {} session file for bot {}: {}",
                    kind,
                    botId,
                    path,
                    exception
            );
            return false;
        }
    }

    private void applyOwnerOnlyPermissions(
            Path path,
            Set<PosixFilePermission> permissions
    ) throws IOException {
        PosixFileAttributeView posix = Files.getFileAttributeView(
                path,
                PosixFileAttributeView.class
        );

        if (posix != null) {
            Files.setPosixFilePermissions(path, permissions);
        }
    }

    private void validateBotId(Long botId) {
        if (botId == null || botId <= 0) {
            throw new IllegalArgumentException(
                    "Bot ID must be positive."
            );
        }
    }
}
