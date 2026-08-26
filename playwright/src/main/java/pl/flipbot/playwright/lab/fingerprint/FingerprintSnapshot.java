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
        int maxTouchPoints,
        int screenWidth,
        int screenHeight,
        int availWidth,
        int availHeight,
        int colorDepth,
        double devicePixelRatio,
        String timezone,
        String userAgent,
        boolean webdriver,
        boolean userAgentDataPresent,
        String userAgentDataPlatform,
        boolean userAgentDataMobile,
        String webglVendor,
        String webglRenderer,
        boolean labMarkerPresent,
        boolean navigatorPlatformGetterLooksNative,
        boolean webglGetParameterLooksNative,
        boolean canvasToDataUrlLooksNative
) {
}
