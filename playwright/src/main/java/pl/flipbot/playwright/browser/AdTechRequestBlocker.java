package pl.flipbot.playwright.browser;

import java.net.URI;
import java.util.List;
import java.util.Locale;

/**
 * Central allow-by-default network filter for advertising / RTB infrastructure.
 *
 * The matcher is intentionally host based. It does not block generic third-party
 * traffic, CDN hosts or marketplace APIs, because those may be required by the
 * normal Vinted UI. A host is blocked only when it is the listed domain itself
 * or one of its subdomains.
 */
public final class AdTechRequestBlocker {

    private static final List<String> BLOCKED_HOST_SUFFIXES = List.of(
            "a-mo.net",
            "doubleclick.net",
            "googlesyndication.com",
            "googleadservices.com",
            "criteo.com",
            "criteo.net",
            "adform.net",
            "rubiconproject.com",
            "pubmatic.com",
            "openx.net",
            "adnxs.com",
            "casalemedia.com",
            "smartadserver.com",
            "sharethrough.com",
            "3lift.com",
            "indexww.com",
            "yieldmo.com",
            "taboola.com",
            "outbrain.com",
            "amazon-adsystem.com"
    );

    private AdTechRequestBlocker() {
    }

    public static boolean shouldBlock(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return false;
        }

        try {
            URI uri = URI.create(rawUrl.trim());
            return shouldBlockHost(uri.getHost());
        } catch (RuntimeException exception) {
            return false;
        }
    }

    static boolean shouldBlockHost(String rawHost) {
        if (rawHost == null || rawHost.isBlank()) {
            return false;
        }

        String host = rawHost
                .trim()
                .toLowerCase(Locale.ROOT);

        for (String blockedSuffix : BLOCKED_HOST_SUFFIXES) {
            if (host.equals(blockedSuffix)
                    || host.endsWith("." + blockedSuffix)) {
                return true;
            }
        }

        return false;
    }

    static List<String> blockedHostSuffixes() {
        return BLOCKED_HOST_SUFFIXES;
    }
}
