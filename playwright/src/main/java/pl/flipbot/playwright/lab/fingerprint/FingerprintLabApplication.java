package pl.flipbot.playwright.lab.fingerprint;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

import java.io.IOException;

/**
 * Standalone local fingerprint laboratory.
 *
 * This application is deliberately separate from FlipBot's marketplace
 * BrowserManager/BotContext pipeline. It compares a baseline Chrome context
 * with a synthetic fingerprint context on a laboratory URL only.
 */
public final class FingerprintLabApplication {

    private static final String URL_ENV = "FLIPBOT_FINGERPRINT_LAB_URL";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private FingerprintLabApplication() {}

    public static void main(String[] args) throws Exception {
        String configuredUrl = configuredTargetUrl(args);
        FingerprintLabServer localServer = null;

        try {
            String targetUrl;

            if (configuredUrl == null) {
                localServer = FingerprintLabServer.startDefault();
                targetUrl = localServer.url();
                System.out.println(
                        "[FINGERPRINT LAB] Local detector server started at "
                                + targetUrl
                );
            } else {
                targetUrl = configuredUrl;
            }

            FingerprintLabPolicy.requireAllowed(targetUrl);
            runLab(targetUrl);

        } finally {
            if (localServer != null) {
                localServer.close();
            }
        }
    }

    private static void runLab(String targetUrl) throws Exception {
        FingerprintLabProfile profile =
                FingerprintLabProfile.demoDesktopProfile();

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(
                    new BrowserType.LaunchOptions()
                            .setHeadless(false)
                            .setChannel("chrome")
            );

            try {
                FingerprintSnapshot baseline = captureBaseline(
                        browser,
                        targetUrl
                );

                try (BrowserContext labContext = createLabContext(
                        browser,
                        profile
                )) {
                    Page labPage = labContext.newPage();
                    labPage.navigate(targetUrl);
                    labPage.waitForLoadState();

                    FingerprintSnapshot simulated =
                            FingerprintLab.capture(labPage);

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
                            "Fingerprint lab is running on: " + targetUrl
                    );
                    System.out.println(
                            "External network requests are blocked. "
                                    + "Press ENTER to close the laboratory browser."
                    );

                    waitForEnter();
                }
            } finally {
                browser.close();
            }
        }
    }

    static BrowserContext createLabContext(
            Browser browser,
            FingerprintLabProfile profile
    ) {
        BrowserContext context = browser.newContext(
                new Browser.NewContextOptions()
                        .setLocale(profile.locale())
                        .setTimezoneId(profile.timezoneId())
                        .setViewportSize(
                                profile.availWidth(),
                                profile.availHeight()
                        )
                        .setDeviceScaleFactor(
                                profile.deviceScaleFactor()
                        )
        );

        installNetworkSafetyBoundary(context);
        context.addInitScript(FingerprintLabScript.build(profile));
        return context;
    }

    private static FingerprintSnapshot captureBaseline(
            Browser browser,
            String targetUrl
    ) {
        try (BrowserContext context = browser.newContext()) {
            installNetworkSafetyBoundary(context);

            Page page = context.newPage();
            page.navigate(targetUrl);
            page.waitForLoadState();

            return FingerprintLab.capture(page);
        }
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
    }

    static boolean isSafeLaboratoryResource(String url) {
        if (FingerprintLabPolicy.isAllowedUrl(url)) {
            return true;
        }

        if (url == null) {
            return false;
        }

        String normalized = url.trim().toLowerCase();

        // Non-network resources created by the already-allowed local document.
        return normalized.startsWith("data:")
                || normalized.startsWith("blob:")
                || normalized.equals("about:blank");
    }

    private static String configuredTargetUrl(String[] args) {
        if (args != null && args.length > 0 && !args[0].isBlank()) {
            return args[0].trim();
        }

        String fromEnv = System.getenv(URL_ENV);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.trim();
        }

        return null;
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
