package pl.flipbot.playwright.lab.fingerprint;

import java.util.List;

/**
 * Coherent synthetic device profile for the controlled fingerprint laboratory.
 *
 * Profile values are realistic enough to exercise consistency checks on a
 * local/test platform, while the runtime remains visibly marked and hard-blocked
 * from production hosts by FingerprintLabPolicy.
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
        if (availWidth > screenWidth || availHeight > screenHeight) {
            throw new IllegalArgumentException(
                    "available screen dimensions cannot exceed screen dimensions"
            );
        }
        if (colorDepth < 1) {
            throw new IllegalArgumentException("colorDepth must be positive");
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

    /**
     * Compatibility alias for the original lab API.
     */
    public static FingerprintLabProfile demoDesktopProfile() {
        return FingerprintLabProfileCatalog.defaultProfile();
    }
}
