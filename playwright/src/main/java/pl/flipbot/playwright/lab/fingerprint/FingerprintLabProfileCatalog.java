package pl.flipbot.playwright.lab.fingerprint;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Coherent synthetic browser profiles for the local fingerprint laboratory.
 *
 * The profiles intentionally stay inside the laboratory safety boundary. They
 * are designed to test consistency across related browser surfaces, not to
 * bypass bot detection on production websites.
 */
public final class FingerprintLabProfileCatalog {

    public static final String DEFAULT_PROFILE_ID = "windows-desktop-pl";

    private static final Map<String, FingerprintLabProfile> PROFILES =
            createProfiles();

    private FingerprintLabProfileCatalog() {}

    public static FingerprintLabProfile defaultProfile() {
        return byId(DEFAULT_PROFILE_ID);
    }

    public static FingerprintLabProfile byId(String rawId) {
        String id = normalizeId(rawId);
        FingerprintLabProfile profile = PROFILES.get(id);

        if (profile == null) {
            throw new IllegalArgumentException(
                    "Unknown fingerprint lab profile '"
                            + rawId
                            + "'. Available profiles: "
                            + String.join(", ", PROFILES.keySet())
            );
        }

        return profile;
    }

    public static Set<String> ids() {
        return PROFILES.keySet();
    }

    static String normalizeId(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            return DEFAULT_PROFILE_ID;
        }
        return rawId.trim().toLowerCase(Locale.ROOT);
    }

    private static Map<String, FingerprintLabProfile> createProfiles() {
        Map<String, FingerprintLabProfile> profiles = new LinkedHashMap<>();

        profiles.put(
                "windows-desktop-pl",
                new FingerprintLabProfile(
                        "Win32",
                        8,
                        8,
                        List.of("pl-PL", "pl", "en-US", "en"),
                        0,
                        1920,
                        1080,
                        1920,
                        1032,
                        24,
                        1.0,
                        "Europe/Warsaw",
                        "Google Inc. (Intel)",
                        "ANGLE (Intel, Intel(R) UHD Graphics Direct3D11)",
                        0x5A17
                )
        );

        profiles.put(
                "windows-laptop-pl",
                new FingerprintLabProfile(
                        "Win32",
                        12,
                        16,
                        List.of("pl-PL", "pl", "en-US", "en"),
                        0,
                        1920,
                        1200,
                        1920,
                        1140,
                        24,
                        1.25,
                        "Europe/Warsaw",
                        "Google Inc. (Intel)",
                        "ANGLE (Intel, Intel(R) Iris(R) Xe Graphics Direct3D11)",
                        0x6B29
                )
        );

        profiles.put(
                "windows-desktop-en",
                new FingerprintLabProfile(
                        "Win32",
                        16,
                        16,
                        List.of("en-US", "en"),
                        0,
                        2560,
                        1440,
                        2560,
                        1392,
                        24,
                        1.0,
                        "America/New_York",
                        "Google Inc. (NVIDIA)",
                        "ANGLE (NVIDIA, NVIDIA GeForce RTX Graphics Direct3D11)",
                        0x7C31
                )
        );

        return Map.copyOf(profiles);
    }
}
