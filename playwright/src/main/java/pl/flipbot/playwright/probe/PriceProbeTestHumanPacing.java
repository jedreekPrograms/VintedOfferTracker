package pl.flipbot.playwright.probe;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import lombok.extern.slf4j.Slf4j;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Optional interaction pacing for a controlled PRICE_PROBE test endpoint.
 *
 * This class intentionally does not alter browser fingerprinting. It only
 * changes interaction timing and is hard-scoped to PriceProbeRuntimeConfig,
 * which rejects vinted.pl and requires the configured test endpoint.
 */
@Slf4j
public final class PriceProbeTestHumanPacing {

    public static final String ENABLED_ENV =
            "FLIPBOT_PRICE_PROBE_TEST_HUMAN_PACING_ENABLED";
    public static final String KEY_DELAY_MIN_ENV =
            "FLIPBOT_PRICE_PROBE_TEST_KEY_DELAY_MIN_MS";
    public static final String KEY_DELAY_MAX_ENV =
            "FLIPBOT_PRICE_PROBE_TEST_KEY_DELAY_MAX_MS";
    public static final String ACTION_PAUSE_MIN_ENV =
            "FLIPBOT_PRICE_PROBE_TEST_ACTION_PAUSE_MIN_MS";
    public static final String ACTION_PAUSE_MAX_ENV =
            "FLIPBOT_PRICE_PROBE_TEST_ACTION_PAUSE_MAX_MS";
    public static final String PAGE_SETTLE_MIN_ENV =
            "FLIPBOT_PRICE_PROBE_TEST_PAGE_SETTLE_MIN_MS";
    public static final String PAGE_SETTLE_MAX_ENV =
            "FLIPBOT_PRICE_PROBE_TEST_PAGE_SETTLE_MAX_MS";

    private static final int DEFAULT_KEY_DELAY_MIN_MS = 45;
    private static final int DEFAULT_KEY_DELAY_MAX_MS = 120;
    private static final int DEFAULT_ACTION_PAUSE_MIN_MS = 220;
    private static final int DEFAULT_ACTION_PAUSE_MAX_MS = 850;
    private static final int DEFAULT_PAGE_SETTLE_MIN_MS = 450;
    private static final int DEFAULT_PAGE_SETTLE_MAX_MS = 1_400;

    private static final int MIN_DELAY_MS = 0;
    private static final int MAX_KEY_DELAY_MS = 500;
    private static final int MAX_ACTION_PAUSE_MS = 5_000;
    private static final int MAX_PAGE_SETTLE_MS = 10_000;

    private final PriceProbeRuntimeConfig config;
    private final boolean enabled;
    private final Range keyDelay;
    private final Range actionPause;
    private final Range pageSettle;

    private PriceProbeTestHumanPacing(
            PriceProbeRuntimeConfig config,
            boolean enabled,
            Range keyDelay,
            Range actionPause,
            Range pageSettle
    ) {
        this.config = config;
        this.enabled = enabled;
        this.keyDelay = keyDelay;
        this.actionPause = actionPause;
        this.pageSettle = pageSettle;
    }

    public static PriceProbeTestHumanPacing fromEnvironment(
            PriceProbeRuntimeConfig config
    ) {
        boolean enabled = readBoolean(ENABLED_ENV, false);

        if (enabled) {
            if (config == null
                    || !config.enabled()
                    || !config.isAllowedUrl(config.homeUrl())) {
                throw new IllegalStateException(
                        ENABLED_ENV
                                + " can run only with PRICE_PROBE enabled on the configured non-Vinted test endpoint."
                );
            }

            if (PriceProbeRuntimeConfig.isRealVintedHost(
                    config.baseUri().getHost()
            )) {
                throw new IllegalStateException(
                        "PRICE_PROBE test human pacing cannot target vinted.pl or its subdomains."
                );
            }
        }

        Range keyDelay = readRange(
                KEY_DELAY_MIN_ENV,
                KEY_DELAY_MAX_ENV,
                DEFAULT_KEY_DELAY_MIN_MS,
                DEFAULT_KEY_DELAY_MAX_MS,
                MAX_KEY_DELAY_MS
        );
        Range actionPause = readRange(
                ACTION_PAUSE_MIN_ENV,
                ACTION_PAUSE_MAX_ENV,
                DEFAULT_ACTION_PAUSE_MIN_MS,
                DEFAULT_ACTION_PAUSE_MAX_MS,
                MAX_ACTION_PAUSE_MS
        );
        Range pageSettle = readRange(
                PAGE_SETTLE_MIN_ENV,
                PAGE_SETTLE_MAX_ENV,
                DEFAULT_PAGE_SETTLE_MIN_MS,
                DEFAULT_PAGE_SETTLE_MAX_MS,
                MAX_PAGE_SETTLE_MS
        );

        if (enabled) {
            log.warn(
                    "[PRICE PROBE TEST] Human pacing ENABLED only for {}. keyDelay={}..{}ms, actionPause={}..{}ms, pageSettle={}..{}ms.",
                    config.baseUri(),
                    keyDelay.min(),
                    keyDelay.max(),
                    actionPause.min(),
                    actionPause.max(),
                    pageSettle.min(),
                    pageSettle.max()
            );
        }

        return new PriceProbeTestHumanPacing(
                config,
                enabled,
                keyDelay,
                actionPause,
                pageSettle
        );
    }

    public boolean enabled() {
        return enabled;
    }

    public void afterNavigation(Page page) {
        if (!isActiveFor(page)) {
            return;
        }
        pause(page, pageSettle);
    }

    public void beforeClick(Page page, Locator locator) {
        requireAllowedInteractionPage(page, "click");

        if (!enabled || locator == null) {
            return;
        }

        try {
            locator.scrollIntoViewIfNeeded();
        } catch (RuntimeException ignored) {
            // Best-effort pacing only; normal Playwright action still decides usability.
        }

        pause(page, actionPause);
        requireAllowedInteractionPage(page, "click after pacing delay");

        try {
            locator.hover();
            pause(page, new Range(
                    Math.min(120, actionPause.min()),
                    Math.min(350, actionPause.max())
            ));
        } catch (RuntimeException ignored) {
            // Hover is optional on the controlled test UI.
        }

        requireAllowedInteractionPage(page, "click after hover pacing");
    }

    public void afterClick(Page page) {
        if (!isActiveFor(page)) {
            return;
        }
        pause(page, actionPause);
    }

    public void typeText(Page page, Locator input, String value) {
        requireAllowedInteractionPage(page, "text entry");

        if (!enabled) {
            input.fill(value);
            return;
        }

        input.fill("");
        pause(page, new Range(120, 320));
        requireAllowedInteractionPage(page, "text entry after initial pacing");

        for (int index = 0; index < value.length(); index++) {
            requireAllowedInteractionPage(page, "paced text entry");

            String character = String.valueOf(value.charAt(index));
            input.pressSequentially(
                    character,
                    new Locator.PressSequentiallyOptions()
                            .setDelay(random(keyDelay))
            );

            if (isNaturalPauseCharacter(value.charAt(index))) {
                pause(page, new Range(80, 260));
            }
        }
    }

    public void shortPause(Page page) {
        if (!isActiveFor(page)) {
            return;
        }
        pause(page, actionPause);
    }

    static boolean allowedFor(
            PriceProbeRuntimeConfig config,
            String rawUrl
    ) {
        return config != null
                && config.enabled()
                && rawUrl != null
                && config.isAllowedUrl(rawUrl)
                && !PriceProbeRuntimeConfig.isRealVintedHost(
                        config.baseUri().getHost()
                );
    }

    private void requireAllowedInteractionPage(
            Page page,
            String action
    ) {
        if (page == null || page.isClosed()) {
            throw new IllegalStateException(
                    "PRICE_PROBE cannot perform " + action + " on a closed page."
            );
        }

        String currentUrl;
        try {
            currentUrl = page.url();
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "PRICE_PROBE could not verify the page origin before "
                            + action + ".",
                    exception
            );
        }

        if (!allowedFor(config, currentUrl)) {
            throw new IllegalStateException(
                    "PRICE_PROBE refused " + action
                            + " because the page is outside the configured non-Vinted test endpoint: "
                            + currentUrl
            );
        }
    }

    private boolean isActiveFor(Page page) {
        if (!enabled || page == null || page.isClosed()) {
            return false;
        }

        try {
            return allowedFor(config, page.url());
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private void pause(Page page, Range range) {
        int delayMs = random(range);
        if (delayMs > 0) {
            page.waitForTimeout(delayMs);
        }
    }

    private static boolean isNaturalPauseCharacter(char character) {
        return character == ' '
                || character == ','
                || character == '.'
                || character == '?'
                || character == '!'
                || character == ':'
                || character == ';';
    }

    private static int random(Range range) {
        if (range.max() <= range.min()) {
            return range.min();
        }

        return ThreadLocalRandom.current().nextInt(
                range.min(), range.max() + 1
        );
    }

    private static Range readRange(
            String minName,
            String maxName,
            int defaultMin,
            int defaultMax,
            int maxAllowed
    ) {
        int min = readBoundedInt(
                minName,
                defaultMin,
                MIN_DELAY_MS,
                maxAllowed
        );
        int max = readBoundedInt(
                maxName,
                defaultMax,
                MIN_DELAY_MS,
                maxAllowed
        );

        return min <= max
                ? new Range(min, max)
                : new Range(max, min);
    }

    private static int readBoundedInt(
            String name,
            int fallback,
            int min,
            int max
    ) {
        String raw = System.getenv(name);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }

        try {
            int parsed = Integer.parseInt(raw.trim());
            return Math.max(min, Math.min(max, parsed));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static boolean readBoolean(String name, boolean fallback) {
        String raw = System.getenv(name);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }

        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "true", "1", "yes", "y", "on" -> true;
            case "false", "0", "no", "n", "off" -> false;
            default -> fallback;
        };
    }

    private record Range(int min, int max) {}
}
