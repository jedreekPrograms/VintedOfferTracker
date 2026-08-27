package pl.flipbot.playwright.lab.fingerprint;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

import java.util.ArrayList;
import java.util.List;

/**
 * Managed local/test-only multi-context runner.
 *
 * This is not a standalone application. ControlledTestModuleManager invokes it
 * from the single FlipBotPlaywrightApplication process.
 */
public final class FingerprintLabFleetRunner {

    public static final String CONTEXT_COUNT_ENV =
            "FLIPBOT_FINGERPRINT_LAB_CONTEXTS";
    public static final String BATCH_SIZE_ENV =
            "FLIPBOT_FINGERPRINT_LAB_BATCH_SIZE";

    private static final int DEFAULT_CONTEXT_COUNT = 20;
    private static final int DEFAULT_BATCH_SIZE = 10;
    private static final int SIMPLE_MODE_BATCH_SIZE = 25;
    private static final int MAX_CONTEXT_COUNT = 1000;
    private static final int MAX_BATCH_SIZE = 100;

    private FingerprintLabFleetRunner() {}

    static void run() {
        boolean simpleRuntime = ControlledTestRuntime.isEnabled();
        if (simpleRuntime) {
            ControlledTestRuntime.requireValidConfiguration();
        }

        FingerprintLabConfiguration base =
                FingerprintLabConfiguration.fromEnvironment(null);
        FingerprintLabServer localServer = null;

        try {
            if (base.targetUrl() == null) {
                localServer = FingerprintLabServer.startDefault();
                base = new FingerprintLabConfiguration(
                        localServer.url(),
                        base.profileId(),
                        false,
                        simpleRuntime || base.humanBehaviorSimulation(),
                        base.proxyUrl()
                );
            }

            FingerprintLabPolicy.requireAllowed(base.targetUrl());
            FingerprintLabPolicy.requireAllowedProxy(base.proxyUrl());

            int total = simpleRuntime
                    ? ControlledTestRuntime.botCount()
                    : boundedPositiveInt(
                    System.getenv(CONTEXT_COUNT_ENV),
                    DEFAULT_CONTEXT_COUNT,
                    MAX_CONTEXT_COUNT,
                    CONTEXT_COUNT_ENV
            );

            int batchSize = simpleRuntime
                    ? Math.min(SIMPLE_MODE_BATCH_SIZE, total)
                    : boundedPositiveInt(
                    System.getenv(BATCH_SIZE_ENV),
                    DEFAULT_BATCH_SIZE,
                    MAX_BATCH_SIZE,
                    BATCH_SIZE_ENV
            );

            System.out.printf(
                    "[FINGERPRINT LAB LOAD] mode=%s target=%s bots=%d batch=%d humanBehavior=%s%n",
                    simpleRuntime ? "simple-controlled" : "advanced-lab",
                    base.targetUrl(),
                    total,
                    batchSize,
                    base.humanBehaviorSimulation()
            );

            runFleet(base, total, batchSize);

        } finally {
            if (localServer != null) {
                localServer.close();
            }
        }
    }

    private static void runFleet(
            FingerprintLabConfiguration base,
            int total,
            int batchSize
    ) {
        List<String> profileIds =
                new ArrayList<>(FingerprintLabProfileCatalog.ids());
        int completed = 0;
        int failures = 0;
        long startedAt = System.nanoTime();

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(
                    new BrowserType.LaunchOptions()
                            .setHeadless(true)
                            .setChannel("chrome")
            );

            try {
                while (completed + failures < total) {
                    int processedBeforeBatch = completed + failures;
                    int remaining = total - processedBeforeBatch;
                    int currentBatchSize = Math.min(batchSize, remaining);
                    int batchSuccesses = 0;
                    List<BrowserContext> contexts = new ArrayList<>();

                    try {
                        for (int index = 0; index < currentBatchSize; index++) {
                            int globalIndex = processedBeforeBatch + index;
                            String profileId = profileIds.get(
                                    globalIndex % profileIds.size()
                            );

                            FingerprintLabConfiguration configuration =
                                    new FingerprintLabConfiguration(
                                            base.targetUrl(),
                                            profileId,
                                            false,
                                            base.humanBehaviorSimulation(),
                                            base.proxyUrl()
                                    );

                            BrowserContext context =
                                    FingerprintLabRuntimeSupport.createLabContext(
                                            browser,
                                            configuration
                                    );
                            contexts.add(context);

                            Page page = context.newPage();
                            page.navigate(base.targetUrl());
                            page.waitForLoadState();

                            FingerprintLabPolicy.requireAllowed(page.url());

                            if (configuration.humanBehaviorSimulation()) {
                                FingerprintLabHumanBehavior.exercise(page);
                            }

                            if (!Boolean.TRUE.equals(page.evaluate(
                                    "() => Boolean(window.__flipbotFingerprintLab?.active)"
                            ))) {
                                throw new IllegalStateException(
                                        "Lab marker missing for context "
                                                + globalIndex
                                );
                            }

                            batchSuccesses++;
                        }

                    } catch (RuntimeException exception) {
                        System.err.println(
                                "[FINGERPRINT LAB LOAD] Batch failed: "
                                        + exception.getMessage()
                        );
                    } finally {
                        for (BrowserContext context : contexts) {
                            try {
                                context.close();
                            } catch (RuntimeException ignored) {
                                // Continue closing the remaining lab contexts.
                            }
                        }
                    }

                    completed += batchSuccesses;
                    failures += currentBatchSize - batchSuccesses;
                    printProgress(completed, failures, total);
                }
            } finally {
                browser.close();
            }
        }

        double seconds = (System.nanoTime() - startedAt) / 1_000_000_000.0;
        System.out.printf(
                "[FINGERPRINT LAB LOAD] finished total=%d success=%d failures=%d elapsed=%.2fs%n",
                total,
                completed,
                failures,
                seconds
        );
    }

    private static void printProgress(
            int completed,
            int failures,
            int total
    ) {
        Runtime runtime = Runtime.getRuntime();
        long usedMb = (
                runtime.totalMemory() - runtime.freeMemory()
        ) / (1024L * 1024L);

        System.out.printf(
                "[FINGERPRINT LAB LOAD] progress=%d/%d failures=%d jvmUsed=%dMB%n",
                completed + failures,
                total,
                failures,
                usedMb
        );
    }

    private static int boundedPositiveInt(
            String raw,
            int fallback,
            int max,
            String name
    ) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }

        try {
            int parsed = Integer.parseInt(raw.trim());
            if (parsed < 1 || parsed > max) {
                throw new IllegalArgumentException(
                        name + " must be between 1 and " + max
                );
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    name + " must be an integer",
                    exception
            );
        }
    }
}
