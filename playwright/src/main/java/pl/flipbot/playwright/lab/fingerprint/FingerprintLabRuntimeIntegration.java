package pl.flipbot.playwright.lab.fingerprint;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.options.ServiceWorkerPolicy;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/**
 * Optional bridge between FlipBot's regular BrowserManager pipeline and the
 * isolated fingerprint laboratory.
 *
 * The bridge is deliberately fail-closed and requires all of the following:
 * - FLIPBOT_FINGERPRINT_RUNTIME_INTEGRATION=true
 * - FLIPBOT_FINGERPRINT_LAB=true
 * - FLIPBOT_FINGERPRINT_LAB_URL set to a URL accepted by FingerprintLabPolicy
 *
 * When active, the same laboratory network boundary is installed on the
 * regular BrowserContext. This prevents the lab-instrumented context from
 * reaching production websites even if later application code attempts to
 * navigate there.
 */
@Slf4j
public final class FingerprintLabRuntimeIntegration {

    public static final String INTEGRATION_ENV =
            "FLIPBOT_FINGERPRINT_RUNTIME_INTEGRATION";
    public static final String TARGET_URL_ENV =
            "FLIPBOT_FINGERPRINT_LAB_URL";

    private FingerprintLabRuntimeIntegration() {}

    public static boolean isRequested() {
        return Boolean.parseBoolean(
                System.getenv().getOrDefault(INTEGRATION_ENV, "false")
        );
    }

    public static boolean isActive() {
        String targetUrl = System.getenv(TARGET_URL_ENV);

        return validateConfiguration(
                isRequested(),
                FingerprintLabPolicy.isEnabled(),
                targetUrl
        );
    }

    static boolean validateConfiguration(
            boolean runtimeIntegrationRequested,
            boolean fingerprintLabEnabled,
            String targetUrl
    ) {
        if (!runtimeIntegrationRequested) {
            return false;
        }

        if (!fingerprintLabEnabled) {
            throw new IllegalStateException(
                    "Fingerprint runtime integration requires "
                            + FingerprintLabPolicy.ENABLE_ENV
                            + "=true."
            );
        }

        if (targetUrl == null || targetUrl.isBlank()) {
            throw new IllegalStateException(
                    "Fingerprint runtime integration requires "
                            + TARGET_URL_ENV
                            + " to point to an allowed laboratory URL."
            );
        }

        if (!FingerprintLabPolicy.isAllowedUrl(targetUrl)) {
            throw new IllegalStateException(
                    "Fingerprint runtime integration refuses non-laboratory URL: "
                            + targetUrl
                            + ". Allowed hosts are loopback, *.localhost and *.test only."
            );
        }

        return true;
    }

    public static void prepareContextOptions(
            Browser.NewContextOptions options
    ) {
        Objects.requireNonNull(options, "options cannot be null");

        if (!isActive()) {
            return;
        }

        FingerprintLabProfile profile =
                FingerprintLabProfile.demoDesktopProfile();

        options
                .setLocale(profile.locale())
                .setTimezoneId(profile.timezoneId())
                .setViewportSize(
                        profile.availWidth(),
                        profile.availHeight()
                )
                .setDeviceScaleFactor(profile.deviceScaleFactor())
                .setServiceWorkers(ServiceWorkerPolicy.BLOCK);
    }

    public static void install(BrowserContext context) {
        Objects.requireNonNull(context, "context cannot be null");

        if (!isActive()) {
            return;
        }

        String targetUrl = configuredTargetUrl();
        FingerprintLabProfile profile =
                FingerprintLabProfile.demoDesktopProfile();

        FingerprintLabApplication.installNetworkSafetyBoundary(context);
        context.addInitScript(FingerprintLabScript.build(profile));

        log.warn(
                "[FINGERPRINT LAB] Runtime integration ACTIVE for laboratory target {}. "
                        + "This BrowserContext is restricted to loopback/reserved test hosts; production HTTP(S)/WebSocket traffic is blocked.",
                targetUrl
        );
    }

    public static String configuredTargetUrl() {
        String targetUrl = System.getenv(TARGET_URL_ENV);

        if (targetUrl == null || targetUrl.isBlank()) {
            throw new IllegalStateException(
                    "Fingerprint runtime integration requires "
                            + TARGET_URL_ENV
                            + " to point to an allowed laboratory URL."
            );
        }

        return targetUrl.trim();
    }
}
