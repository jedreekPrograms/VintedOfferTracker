package pl.flipbot.playwright.worker;

import java.util.concurrent.TimeUnit;

final class ScheduledJobFailureBackoffPolicy {

    private static final long[] CATALOG_BACKOFF_MINUTES = {2L, 5L, 10L, 15L};
    private static final long[] NEGOTIATION_BACKOFF_MINUTES = {1L, 2L, 5L, 10L};

    private ScheduledJobFailureBackoffPolicy() {
    }

    static long delayMillis(
            ScheduledJobType jobType,
            int consecutiveFailures,
            long configuredFallbackDelayMillis
    ) {
        if (jobType == null) {
            throw new IllegalArgumentException("Job type is required.");
        }
        if (consecutiveFailures < 1) {
            throw new IllegalArgumentException(
                    "Consecutive failure count must be at least 1."
            );
        }

        long configuredMinimum = Math.max(0L, configuredFallbackDelayMillis);
        long policyDelay = switch (jobType) {
            case CATALOG_SCAN -> delayFromMinutes(
                    CATALOG_BACKOFF_MINUTES,
                    consecutiveFailures
            );
            case NEGOTIATION_CHECK -> delayFromMinutes(
                    NEGOTIATION_BACKOFF_MINUTES,
                    consecutiveFailures
            );
            case PRICE_PROBE -> configuredMinimum;
        };

        return Math.max(configuredMinimum, policyDelay);
    }

    private static long delayFromMinutes(
            long[] scheduleMinutes,
            int consecutiveFailures
    ) {
        int index = Math.min(
                consecutiveFailures - 1,
                scheduleMinutes.length - 1
        );

        return TimeUnit.MINUTES.toMillis(scheduleMinutes[index]);
    }
}
