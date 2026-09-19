package pl.flipbot.playwright.browser;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * Conservative blocker for third-party advertising / RTB infrastructure that
 * has been observed opening redirect tabs from Vinted.
 *
 * <p>The policy intentionally blocks only known ad-tech hosts. Vinted itself,
 * generic Google traffic and CAPTCHA/challenge infrastructure are not matched.
 * This keeps authentication and marketplace functionality fail-open while
 * preventing the specific advertising chains observed in production.</p>
 */
final class AdTechRequestPolicy {

    private static final Set<String> BLOCKED_HOST_SUFFIXES = Set.of(
            "3lift.com",
            "4dex.io",
            "adtrafficquality.google",
            "doubleclick.net",
            "googlesyndication.com",
            "openx.net",
            "pbstck.com",
            "seedtag.com"
    );

    private AdTechRequestPolicy() {
    }

    static boolean shouldBlock(String requestUrl) {
        if (requestUrl == null || requestUrl.isBlank()) {
            return false;
        }

        try {
            URI uri = URI.create(requestUrl.trim());
            String scheme = normalize(uri.getScheme());
            String host = normalize(uri.getHost());

            if (!("http".equals(scheme) || "https".equals(scheme))
                    || host.isBlank()) {
                return false;
            }

            for (String blockedSuffix : BLOCKED_HOST_SUFFIXES) {
                if (host.equals(blockedSuffix)
                        || host.endsWith("." + blockedSuffix)) {
                    return true;
                }
            }

            return false;
        } catch (RuntimeException exception) {
            /*
             * Browser correctness must never depend on parsing an unusual URL.
             * Unknown/malformed traffic is allowed and remains covered by the
             * single-page popup fail-safe.
             */
            return false;
        }
    }

    static Set<String> blockedHostSuffixes() {
        return BLOCKED_HOST_SUFFIXES;
    }

    private static String normalize(String value) {
        return value == null
                ? ""
                : value.trim().toLowerCase(Locale.ROOT);
    }
}
