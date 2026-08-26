package pl.flipbot.playwright.lab.fingerprint;

import java.util.List;

/**
 * Small, explicit demo profile used only by the local fingerprint laboratory.
 * Values are deliberately obvious so a test page can show exactly which
 * browser surfaces changed.
 */
public record FingerprintLabProfile(
        String platform,
        int hardwareConcurrency,
        int deviceMemoryGb,
        List<String> languages
) {

    public FingerprintLabProfile {
        if (platform == null || platform.isBlank()) {
            throw new IllegalArgumentException("platform cannot be blank");
        }
        if (hardwareConcurrency < 1) {
            throw new IllegalArgumentException(
                    "hardwareConcurrency must be positive"
            );
        }
        if (deviceMemoryGb < 1) {
            throw new IllegalArgumentException(
                    "deviceMemoryGb must be positive"
            );
        }
        if (languages == null || languages.isEmpty()) {
            throw new IllegalArgumentException("languages cannot be empty");
        }

        languages = List.copyOf(languages);
    }

    public static FingerprintLabProfile demoDesktopProfile() {
        return new FingerprintLabProfile(
                "Win32-Lab",
                8,
                8,
                List.of("pl-PL", "pl", "en-US", "en")
        );
    }
}
