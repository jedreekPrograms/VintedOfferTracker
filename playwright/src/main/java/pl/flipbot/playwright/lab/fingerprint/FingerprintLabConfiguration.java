package pl.flipbot.playwright.lab.fingerprint;

import java.nio.file.Path;

/**
 * Single source of truth for local fingerprint-lab configuration.
 */
public record FingerprintLabConfiguration(
        String targetUrl,
        String profileId,
        boolean persistentSession,
        boolean humanBehaviorSimulation,
        String proxyUrl
) {

    public static final String TARGET_URL_ENV = "FLIPBOT_FINGERPRINT_LAB_URL";
    public static final String PROFILE_ENV = "FLIPBOT_FINGERPRINT_LAB_PROFILE";
    public static final String PERSIST_SESSION_ENV =
            "FLIPBOT_FINGERPRINT_LAB_PERSIST_SESSION";
    public static final String HUMAN_BEHAVIOR_ENV =
            "FLIPBOT_FINGERPRINT_LAB_HUMAN_BEHAVIOR";
    public static final String PROXY_URL_ENV =
            "FLIPBOT_FINGERPRINT_LAB_PROXY_URL";

    public FingerprintLabConfiguration {
        profileId = FingerprintLabProfileCatalog.normalizeId(profileId);
        FingerprintLabProfileCatalog.byId(profileId);

        if (targetUrl != null) {
            targetUrl = targetUrl.trim();
            if (targetUrl.isBlank()) {
                targetUrl = null;
            }
        }

        if (proxyUrl != null) {
            proxyUrl = proxyUrl.trim();
            if (proxyUrl.isBlank()) {
                proxyUrl = null;
            }
        }
    }

    public static FingerprintLabConfiguration fromEnvironment(String[] args) {
        boolean simpleRuntime = ControlledTestRuntime.isEnabled();
        if (simpleRuntime) {
            ControlledTestRuntime.requireValidConfiguration();
        }

        String targetUrl = firstNonBlank(
                firstArgument(args),
                firstNonBlank(
                        simpleRuntime ? ControlledTestRuntime.targetUrl() : null,
                        System.getenv(TARGET_URL_ENV)
                )
        );

        String profileId = firstNonBlank(
                System.getenv(PROFILE_ENV),
                FingerprintLabProfileCatalog.DEFAULT_PROFILE_ID
        );

        boolean persistentSession = Boolean.parseBoolean(
                System.getenv().getOrDefault(
                        PERSIST_SESSION_ENV,
                        "false"
                )
        );

        /*
         * The simple three-variable mode should work without another toggle.
         * When an encryption key already exists, warm cookie/localStorage state
         * is enabled automatically. Without a key the runtime stays ephemeral
         * instead of failing startup just because persistence cannot be encrypted.
         */
        if (simpleRuntime && ControlledTestRuntime.hasSessionEncryptionKey()) {
            persistentSession = true;
        }

        boolean humanBehaviorSimulation = simpleRuntime
                || Boolean.parseBoolean(
                System.getenv().getOrDefault(
                        HUMAN_BEHAVIOR_ENV,
                        "false"
                )
        );

        return new FingerprintLabConfiguration(
                targetUrl,
                profileId,
                persistentSession,
                humanBehaviorSimulation,
                firstNonBlank(System.getenv(PROXY_URL_ENV), null)
        );
    }

    public FingerprintLabProfile profile() {
        return FingerprintLabProfileCatalog.byId(profileId);
    }

    public Path sessionStatePath() {
        return Path.of(
                "sessions",
                "fingerprint-lab",
                profileId + ".state.enc"
        );
    }

    private static String firstArgument(String[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        return args[0];
    }

    private static String firstNonBlank(String first, String fallback) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        return fallback == null ? null : fallback.trim();
    }
}
