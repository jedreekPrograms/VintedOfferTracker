package pl.flipbot.playwright.lab.fingerprint;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.Proxy;
import com.microsoft.playwright.options.ServiceWorkerPolicy;

import java.io.IOException;

/**
 * Standalone fingerprint laboratory for loopback / reserved test hosts.
 *
 * It combines the original fingerprint simulator, detector, runtime bridge,
 * coherent synthetic profiles, optional encrypted session persistence,
 * laboratory-only proxy routing and an optional human-interaction simulator.
 * The network boundary remains fail-closed for production hosts.
 */
public final class FingerprintLabApplication {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private FingerprintLabApplication() {}

    public static void main(String[] args) throws Exception {
        FingerprintLabConfiguration configuration =
                FingerprintLabConfiguration.fromEnvironment(args);
        FingerprintLabServer localServer = null;

        try {
            if (configuration.targetUrl() == null) {
                localServer = FingerprintLabServer.startDefault();
                configuration = withTarget(
                        configuration,
                        localServer.url()
                );
                System.out.println(
                        "[FINGERPRINT LAB] Local detector server started at "
                                + configuration.targetUrl()
                );
            }

            validateConfiguration(configuration);
            runLab(configuration);

        } finally {
            if (localServer != null) {
                localServer.close();
            }
        }
    }

    private static void runLab(
            FingerprintLabConfiguration configuration
    ) throws Exception {
        FingerprintLabProfile profile = configuration.profile();

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(
                    new BrowserType.LaunchOptions()
                            .setHeadless(false)
                            .setChannel("chrome")
            );

            try {
                FingerprintSnapshot baseline = captureBaseline(
                        browser,
                        configuration
                );

                try (BrowserContext labContext = createLabContext(
                        browser,
                        configuration
                )) {
                    Page labPage = labContext.newPage();
                    labPage.navigate(configuration.targetUrl());
                    labPage.waitForLoadState();

                    if (configuration.humanBehaviorSimulation()) {
                        FingerprintLabHumanBehavior.exercise(labPage);
                    }

                    FingerprintSnapshot simulated =
                            FingerprintLab.capture(labPage);

                    System.out.println("=== LAB CONFIGURATION ===");
                    System.out.println(
                            "target=" + configuration.targetUrl()
                    );
                    System.out.println(
                            "profile=" + configuration.profileId()
                    );
                    System.out.println(
                            "availableProfiles="
                                    + FingerprintLabProfileCatalog.ids()
                    );
                    System.out.println(
                            "persistentSession="
                                    + configuration.persistentSession()
                    );
                    System.out.println(
                            "humanBehaviorSimulation="
                                    + configuration.humanBehaviorSimulation()
                    );
                    System.out.println(
                            "proxy="
                                    + (configuration.proxyUrl() == null
                                            ? "none"
                                            : configuration.proxyUrl())
                    );
                    System.out.println();

                    System.out.println("=== BASELINE ===");
                    System.out.println(
                            OBJECT_MAPPER.writeValueAsString(baseline)
                    );
                    System.out.println();
                    System.out.println("=== LAB SIMULATION ===");
                    System.out.println(
                            OBJECT_MAPPER.writeValueAsString(simulated)
                    );
                    System.out.println();
                    System.out.println(
                            "Fingerprint lab is running on: "
                                    + configuration.targetUrl()
                    );
                    System.out.println(
                            "HTTP(S), WebSocket and Service Worker escape paths are constrained. "
                                    + "Press ENTER to save enabled lab session state and close the browser."
                    );

                    waitForEnter();

                    FingerprintLabSessionStore.save(
                            configuration,
                            labContext.storageState()
                    );
                }
            } finally {
                browser.close();
            }
        }
    }

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

    /**
     * Compatibility overload kept for the original lab tests/callers.
     */
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

    private static FingerprintSnapshot captureBaseline(
            Browser browser,
            FingerprintLabConfiguration configuration
    ) {
        Browser.NewContextOptions options = new Browser.NewContextOptions()
                .setServiceWorkers(ServiceWorkerPolicy.BLOCK);
        applyLaboratoryProxy(options, configuration);

        try (BrowserContext context = browser.newContext(options)) {
            installNetworkSafetyBoundary(context);

            Page page = context.newPage();
            page.navigate(configuration.targetUrl());
            page.waitForLoadState();

            return FingerprintLab.capture(page);
        }
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

    private static FingerprintLabConfiguration withTarget(
            FingerprintLabConfiguration configuration,
            String targetUrl
    ) {
        return new FingerprintLabConfiguration(
                targetUrl,
                configuration.profileId(),
                configuration.persistentSession(),
                configuration.humanBehaviorSimulation(),
                configuration.proxyUrl()
        );
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

    private static void waitForEnter() {
        try {
            System.in.read();
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not read laboratory shutdown input",
                    exception
            );
        }
    }

    private static String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }
}
