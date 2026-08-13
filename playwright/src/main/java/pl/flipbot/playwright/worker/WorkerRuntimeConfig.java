package pl.flipbot.playwright.worker;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public record WorkerRuntimeConfig(
        int workerCount,
        long syncIntervalSeconds,
        long normalRunDelaySeconds,
        long failureDelaySeconds,
        long rateLimitDelaySeconds,
        long shutdownTimeoutSeconds
) {

    private static final int DEFAULT_WORKER_COUNT = 10;
    private static final int MAX_WORKER_COUNT = 100;

    private static final long DEFAULT_SYNC_INTERVAL_SECONDS = 5L;
    private static final long DEFAULT_NORMAL_RUN_DELAY_SECONDS = 30L;
    private static final long DEFAULT_FAILURE_DELAY_SECONDS = 60L;
    private static final long DEFAULT_RATE_LIMIT_DELAY_SECONDS = 10L * 60L;
    private static final long DEFAULT_SHUTDOWN_TIMEOUT_SECONDS = 30L;


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
                        "FLIPBOT_RUN_INTERVAL_SECONDS",
                        DEFAULT_NORMAL_RUN_DELAY_SECONDS,
                        0L,
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
                )
        );
    }


    private static int readInt(
            String environmentName,
            int defaultValue,
            int minimum,
            int maximum
    ) {

        String rawValue =
                System.getenv(
                        environmentName
                );


        if (
                rawValue == null
                        || rawValue.isBlank()
        ) {

            return defaultValue;
        }


        try {

            int parsedValue =
                    Integer.parseInt(
                            rawValue.trim()
                    );


            if (
                    parsedValue < minimum
                            || parsedValue > maximum
            ) {

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

        String rawValue =
                System.getenv(
                        environmentName
                );


        if (
                rawValue == null
                        || rawValue.isBlank()
        ) {

            return defaultValue;
        }


        try {

            long parsedValue =
                    Long.parseLong(
                            rawValue.trim()
                    );


            if (
                    parsedValue < minimum
                            || parsedValue > maximum
            ) {

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
}
