package pl.flipbot.playwright.lab.fingerprint;

import java.util.List;

/**
 * Observable browser fingerprint surfaces captured by the local lab.
 */
public record FingerprintSnapshot(
        String platform,
        int hardwareConcurrency,
        Integer deviceMemoryGb,
        String language,
        List<String> languages,
        String userAgent,
        boolean labMarkerPresent
) {
}
