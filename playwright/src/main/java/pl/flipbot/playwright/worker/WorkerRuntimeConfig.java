package pl.flipbot.playwright.worker;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public record WorkerRuntimeConfig(
        int workerCount,
        long syncIntervalSeconds,
        long negotiationCheckIntervalSeconds,
        long catalogScanIntervalSeconds,
        long failureDelaySeconds,
        long rateLimitDelaySeconds,
        long shutdownTimeoutSeconds,
        boolean schedulerHeadless
) {

    private static final int DEFAULT_WORKER_COUNT = 10;
    private static final int MAX_WORKER_COUNT = 100;

    private static final long DEFAULT_SYNC_INTERVAL_SECONDS = 5L;
    private static final long DEFAULT_NEGOTIATION_CHECK_INTERVAL_SECONDS = 120L;
    private static final long DEFAULT_CATALOG_SCAN_INTERVAL_SECONDS = 15L * 60L;
    private static final long DEFAULT_FAILURE_DELAY_SECONDS = 60L;
    private static final long DEFAULT_RATE_LIMIT_DELAY_SECONDS = 10L * 60L;
    private static final long DEFAULT_SHUTDOWN_TIMEOUT_SECONDS = 30L;
    private static final boolean DEFAULT_SCHEDULER_HEADLESS = true;

    public static WorkerRuntimeConfig fromEnvironment() {

        return new WorkerRuntimeConfig(
                readInt(
                        "FLIPBOT_WORKER_COUNT",
                        DEFAULT_WORKER_COUNT,
                        1,
                        MAX_WORKER_COUNT
                ),
                readLong(
                        "FLIPBOT_SYNC_INTERVAL_SECONDS",
                        DEFAULT_SYNC_INTERVAL_SECONDS,
                        1L,
                        60L
                ),
                readLong(
                        "FLIPBOT_NEGOTIATION_CHECK_INTERVAL_SECONDS",
                        DEFAULT_NEGOTIATION_CHECK_INTERVAL_SECONDS,
                        30L,
                        60L * 60L
                ),
                readLong(
                        "FLIPBOT_CATALOG_SCAN_INTERVAL_SECONDS",
                        DEFAULT_CATALOG_SCAN_INTERVAL_SECONDS,
                        60L,
                        24L * 60L * 60L
                ),
                readLong(
                        "FLIPBOT_FAILURE_RETRY_SECONDS",
                        DEFAULT_FAILURE_DELAY_SECONDS,
                        1L,
                        24L * 60L * 60L
                ),
                readLong(
                        "FLIPBOT_RATE_LIMIT_RETRY_SECONDS",
                        DEFAULT_RATE_LIMIT_DELAY_SECONDS,
                        1L,
                        24L * 60L * 60L
                ),
                readLong(
                        "FLIPBOT_SHUTDOWN_TIMEOUT_SECONDS",
                        DEFAULT_SHUTDOWN_TIMEOUT_SECONDS,
                        1L,
                        300L
                ),
                readBoolean(
                        "FLIPBOT_SCHEDULER_HEADLESS",
                        DEFAULT_SCHEDULER_HEADLESS
                )
        );
    }

    public long normalDelaySeconds(
            ScheduledJobType jobType
    ) {
        return switch (jobType) {
            case NEGOTIATION_CHECK -> negotiationCheckIntervalSeconds;
            case CATALOG_SCAN -> catalogScanIntervalSeconds;
        };
    }

    private static int readInt(
            String environmentName,
            int defaultValue,
            int minimum,
            int maximum
    ) {

        String rawValue = System.getenv(environmentName);

        if (rawValue == null || rawValue.isBlank()) {
            return defaultValue;
        }

        try {
            int parsedValue = Integer.parseInt(rawValue.trim());

            if (parsedValue < minimum || parsedValue > maximum) {
                throw new IllegalArgumentException(
                        "Value is outside allowed range "
                                + minimum
                                + ".."
                                + maximum
                );
            }

            return parsedValue;

        } catch (RuntimeException exception) {
            log.warn(
                    "Invalid {}='{}'. Using default {}.",
                    environmentName,
                    rawValue,
                    defaultValue
            );

            return defaultValue;
        }
    }

    private static long readLong(
            String environmentName,
            long defaultValue,
            long minimum,
            long maximum
    ) {

        String rawValue = System.getenv(environmentName);

        if (rawValue == null || rawValue.isBlank()) {
            return defaultValue;
        }

        try {
            long parsedValue = Long.parseLong(rawValue.trim());

            if (parsedValue < minimum || parsedValue > maximum) {
                throw new IllegalArgumentException(
                        "Value is outside allowed range "
                                + minimum
                                + ".."
                                + maximum
                );
            }

            return parsedValue;

        } catch (RuntimeException exception) {
            log.warn(
                    "Invalid {}='{}'. Using default {}.",
                    environmentName,
                    rawValue,
                    defaultValue
            );

            return defaultValue;
        }
    }

    private static boolean readBoolean(
            String environmentName,
            boolean defaultValue
    ) {
        String rawValue = System.getenv(environmentName);

        if (rawValue == null || rawValue.isBlank()) {
            return defaultValue;
        }

        String normalized = rawValue.trim().toLowerCase();

        return switch (normalized) {
            case "true", "1", "yes", "y", "on" -> true;
            case "false", "0", "no", "n", "off" -> false;
            default -> {
                log.warn(
                        "Invalid {}='{}'. Using default {}.",
                        environmentName,
                        rawValue,
                        defaultValue
                );
                yield defaultValue;
            }
        };
    }
}
