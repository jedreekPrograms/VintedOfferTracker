package pl.flipbot.playwright.lab.fingerprint;

import java.nio.file.Path;

/**
 * Single source of truth for local fingerprint-lab configuration.
 */
public record FingerprintLabConfiguration(
        String targetUrl,
        String profileId,
        boolean persistentSession,
        boolean humanBehaviorSimulation
) {

    public static final String TARGET_URL_ENV = "FLIPBOT_FINGERPRINT_LAB_URL";
    public static final String PROFILE_ENV = "FLIPBOT_FINGERPRINT_LAB_PROFILE";
    public static final String PERSIST_SESSION_ENV =
            "FLIPBOT_FINGERPRINT_LAB_PERSIST_SESSION";
    public static final String HUMAN_BEHAVIOR_ENV =
            "FLIPBOT_FINGERPRINT_LAB_HUMAN_BEHAVIOR";

    public FingerprintLabConfiguration {
        profileId = FingerprintLabProfileCatalog.normalizeId(profileId);
        FingerprintLabProfileCatalog.byId(profileId);

        if (targetUrl != null) {
            targetUrl = targetUrl.trim();
            if (targetUrl.isBlank()) {
                targetUrl = null;
            }
        }
    }

    public static FingerprintLabConfiguration fromEnvironment(String[] args) {
        String targetUrl = firstNonBlank(
                firstArgument(args),
                System.getenv(TARGET_URL_ENV)
        );

        String profileId = firstNonBlank(
                System.getenv(PROFILE_ENV),
                FingerprintLabProfileCatalog.DEFAULT_PROFILE_ID
        );

        return new FingerprintLabConfiguration(
                targetUrl,
                profileId,
                Boolean.parseBoolean(
                        System.getenv().getOrDefault(
                                PERSIST_SESSION_ENV,
                                "true"
                        )
                ),
                Boolean.parseBoolean(
                        System.getenv().getOrDefault(
                                HUMAN_BEHAVIOR_ENV,
                                "false"
                        )
                )
        );
    }

    public FingerprintLabProfile profile() {
        return FingerprintLabProfileCatalog.byId(profileId);
    }

    public Path sessionStatePath() {
        return Path.of(
                "sessions",
                "fingerprint-lab",
                profileId + ".json"
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
