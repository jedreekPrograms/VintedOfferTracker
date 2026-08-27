package pl.flipbot.playwright.lab.fingerprint;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.options.Proxy;
import com.microsoft.playwright.options.ServiceWorkerPolicy;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/**
 * Optional bridge between FlipBot's regular BrowserManager pipeline and the
 * isolated fingerprint laboratory.
 *
 * Advanced mode keeps the original flags. Simple controlled mode needs only:
 * - FLIPBOT_TEST_AUTOMATION=true
 * - FLIPBOT_TEST_URL=<loopback / *.localhost / *.test URL>
 * - FLIPBOT_TEST_BOTS=<1..350> for the fleet harness
 *
 * Production hosts remain unreachable because FingerprintLabPolicy and the
 * network boundary are still authoritative.
 */
@Slf4j
public final class FingerprintLabRuntimeIntegration {

    public static final String INTEGRATION_ENV =
            "FLIPBOT_FINGERPRINT_RUNTIME_INTEGRATION";
    public static final String TARGET_URL_ENV =
            FingerprintLabConfiguration.TARGET_URL_ENV;

    private FingerprintLabRuntimeIntegration() {}

    public static boolean isRequested() {
        return ControlledTestRuntime.isEnabled()
                || Boolean.parseBoolean(
                System.getenv().getOrDefault(INTEGRATION_ENV, "false")
        );
    }

    public static boolean isActive() {
        String targetUrl = ControlledTestRuntime.isEnabled()
                ? ControlledTestRuntime.targetUrl()
                : System.getenv(TARGET_URL_ENV);

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
                    "Fingerprint runtime integration requires the controlled test runtime or "
                            + FingerprintLabPolicy.ENABLE_ENV
                            + "=true."
            );
        }

        if (targetUrl == null || targetUrl.isBlank()) {
            throw new IllegalStateException(
                    "Fingerprint runtime integration requires a controlled laboratory target URL."
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

        FingerprintLabConfiguration configuration = configuration();
        FingerprintLabProfile profile = configuration.profile();

        options
                .setLocale(profile.locale())
                .setTimezoneId(profile.timezoneId())
                .setViewportSize(
                        profile.availWidth(),
                        profile.availHeight()
                )
                .setDeviceScaleFactor(profile.deviceScaleFactor())
                .setServiceWorkers(ServiceWorkerPolicy.BLOCK);

        if (configuration.proxyUrl() != null) {
            options.setProxy(new Proxy(configuration.proxyUrl()));
        }
    }

    public static void install(BrowserContext context) {
        Objects.requireNonNull(context, "context cannot be null");

        if (!isActive()) {
            return;
        }

        FingerprintLabConfiguration configuration = configuration();
        FingerprintLabProfile profile = configuration.profile();

        FingerprintLabApplication.installNetworkSafetyBoundary(context);
        context.addInitScript(FingerprintLabScript.build(profile));

        log.warn(
                "[FINGERPRINT LAB] Runtime integration ACTIVE for controlled target {} using profile {}{}; production HTTP(S)/WebSocket traffic is blocked.",
                configuration.targetUrl(),
                configuration.profileId(),
                configuration.proxyUrl() == null
                        ? ""
                        : " via laboratory proxy " + configuration.proxyUrl()
        );
    }

    public static FingerprintLabConfiguration configuration() {
        if (ControlledTestRuntime.isEnabled()) {
            ControlledTestRuntime.requireValidConfiguration();
        }

        FingerprintLabConfiguration configuration =
                FingerprintLabConfiguration.fromEnvironment(null);

        if (configuration.targetUrl() == null) {
            throw new IllegalStateException(
                    "Fingerprint runtime integration requires a controlled laboratory target URL."
            );
        }

        FingerprintLabPolicy.requireAllowed(configuration.targetUrl());
        FingerprintLabPolicy.requireAllowedProxy(configuration.proxyUrl());
        return configuration;
    }

    public static String configuredTargetUrl() {
        return configuration().targetUrl();
    }
}
