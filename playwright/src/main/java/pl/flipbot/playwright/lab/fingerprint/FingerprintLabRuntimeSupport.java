package pl.flipbot.playwright.lab.fingerprint;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.options.Proxy;
import com.microsoft.playwright.options.ServiceWorkerPolicy;

/**
 * Shared guarded runtime helpers for fingerprint simulation.
 *
 * This class is intentionally not an application entry point. It is invoked
 * only from FlipBotPlaywrightApplication through the normal runtime managers.
 */
public final class FingerprintLabRuntimeSupport {

    private FingerprintLabRuntimeSupport() {}

    static BrowserContext createLabContext(
            Browser browser,
            FingerprintLabConfiguration configuration
    ) {
        validateConfiguration(configuration);

        FingerprintLabProfile profile = configuration.profile();
        Browser.NewContextOptions options = new Browser.NewContextOptions()
                .setLocale(profile.locale())
                .setTimezoneId(profile.timezoneId())
                .setViewportSize(
                        profile.availWidth(),
                        profile.availHeight()
                )
                .setDeviceScaleFactor(profile.deviceScaleFactor())
                .setServiceWorkers(ServiceWorkerPolicy.BLOCK);

        FingerprintLabSessionStore.load(configuration)
                .ifPresent(options::setStorageState);
        applyLaboratoryProxy(options, configuration);

        BrowserContext context = browser.newContext(options);
        installNetworkSafetyBoundary(context);
        context.addInitScript(FingerprintLabScript.build(profile));
        return context;
    }

    static BrowserContext createLabContext(
            Browser browser,
            FingerprintLabProfile profile
    ) {
        Browser.NewContextOptions options = new Browser.NewContextOptions()
                .setLocale(profile.locale())
                .setTimezoneId(profile.timezoneId())
                .setViewportSize(
                        profile.availWidth(),
                        profile.availHeight()
                )
                .setDeviceScaleFactor(profile.deviceScaleFactor())
                .setServiceWorkers(ServiceWorkerPolicy.BLOCK);

        BrowserContext context = browser.newContext(options);
        installNetworkSafetyBoundary(context);
        context.addInitScript(FingerprintLabScript.build(profile));
        return context;
    }

    private static void applyLaboratoryProxy(
            Browser.NewContextOptions options,
            FingerprintLabConfiguration configuration
    ) {
        if (configuration.proxyUrl() == null) {
            return;
        }

        FingerprintLabPolicy.requireAllowedProxy(configuration.proxyUrl());
        options.setProxy(new Proxy(configuration.proxyUrl()));
    }

    private static void validateConfiguration(
            FingerprintLabConfiguration configuration
    ) {
        if (configuration == null) {
            throw new IllegalArgumentException(
                    "Fingerprint lab configuration cannot be null"
            );
        }
        if (configuration.targetUrl() == null) {
            throw new IllegalStateException(
                    "Fingerprint lab target URL is required"
            );
        }

        FingerprintLabPolicy.requireAllowed(configuration.targetUrl());
        FingerprintLabPolicy.requireAllowedProxy(configuration.proxyUrl());
    }

    static void installNetworkSafetyBoundary(BrowserContext context) {
        context.route(
                "**/*",
                route -> {
                    String url = route.request().url();

                    if (isSafeLaboratoryResource(url)) {
                        route.resume();
                        return;
                    }

                    System.err.println(
                            "[FINGERPRINT LAB] Blocked external request: "
                                    + abbreviate(url, 220)
                    );
                    route.abort();
                }
        );

        context.routeWebSocket(
                "**/*",
                webSocket -> {
                    String url = webSocket.url();

                    if (FingerprintLabPolicy.isAllowedWebSocketUrl(url)) {
                        webSocket.connectToServer();
                        return;
                    }

                    System.err.println(
                            "[FINGERPRINT LAB] Blocked external WebSocket: "
                                    + abbreviate(url, 220)
                    );
                    webSocket.close();
                }
        );
    }

    static boolean isSafeLaboratoryResource(String url) {
        if (FingerprintLabPolicy.isAllowedUrl(url)) {
            return true;
        }

        if (url == null) {
            return false;
        }

        String normalized = url.trim().toLowerCase();

        return normalized.startsWith("data:")
                || normalized.startsWith("blob:")
                || normalized.equals("about:blank");
    }

    private static String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }
}
