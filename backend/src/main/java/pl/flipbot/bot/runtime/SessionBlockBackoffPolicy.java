package pl.flipbot.bot.runtime;

import java.time.Duration;

public final class SessionBlockBackoffPolicy {

    private static final Duration INITIAL_DELAY = Duration.ofMinutes(15);
    private static final Duration MAX_DELAY = Duration.ofDays(7);

    public Duration delayForAttempt(int attemptNumber) {
        if (attemptNumber < 1) {
            throw new IllegalArgumentException("Session block attempt number must be positive.");
        }

        Duration delay = INITIAL_DELAY;

        for (int attempt = 1; attempt < attemptNumber; attempt++) {
            if (delay.compareTo(MAX_DELAY.dividedBy(2)) > 0) {
                return MAX_DELAY;
            }

            delay = delay.multipliedBy(2);
        }

        return delay.compareTo(MAX_DELAY) > 0
                ? MAX_DELAY
                : delay;
    }
}
