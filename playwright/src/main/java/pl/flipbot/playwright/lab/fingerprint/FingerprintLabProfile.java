package pl.flipbot.playwright.lab.fingerprint;

import java.util.List;

/**
 * Synthetic profile for the local fingerprint laboratory.
 *
 * The values intentionally carry a visible "Lab" identity rather than trying
 * to impersonate a real device. The point is to demonstrate which browser
 * surfaces can be modified and how a detector can catch inconsistencies.
 */
public record FingerprintLabProfile(
        String platform,
        int hardwareConcurrency,
        int deviceMemoryGb,
        List<String> languages,
        int maxTouchPoints,
        int screenWidth,
        int screenHeight,
        int availWidth,
        int availHeight,
        int colorDepth,
        double deviceScaleFactor,
        String timezoneId,
        String webglVendor,
        String webglRenderer,
        int canvasNoiseSeed
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
        if (screenWidth < 1 || screenHeight < 1) {
            throw new IllegalArgumentException(
                    "screen dimensions must be positive"
            );
        }
        if (availWidth < 1 || availHeight < 1) {
            throw new IllegalArgumentException(
                    "available screen dimensions must be positive"
            );
        }
        if (deviceScaleFactor <= 0) {
            throw new IllegalArgumentException(
                    "deviceScaleFactor must be positive"
            );
        }
        if (timezoneId == null || timezoneId.isBlank()) {
            throw new IllegalArgumentException("timezoneId cannot be blank");
        }
        if (webglVendor == null || webglVendor.isBlank()) {
            throw new IllegalArgumentException("webglVendor cannot be blank");
        }
        if (webglRenderer == null || webglRenderer.isBlank()) {
            throw new IllegalArgumentException("webglRenderer cannot be blank");
        }

        languages = List.copyOf(languages);
    }

    public String locale() {
        return languages.getFirst();
    }

    public static FingerprintLabProfile demoDesktopProfile() {
        return new FingerprintLabProfile(
                "Win32-Lab",
                8,
                8,
                List.of("pl-PL", "pl", "en-US", "en"),
                0,
                1920,
                1080,
                1920,
                1040,
                24,
                1.0,
                "Europe/Warsaw",
                "FlipBot Lab GPU Vendor",
                "FlipBot Lab GPU Renderer",
                0x5A17
        );
    }
}
