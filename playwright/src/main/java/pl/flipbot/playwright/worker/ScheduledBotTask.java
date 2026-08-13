package pl.flipbot.playwright.worker;

import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

public record ScheduledBotTask(
        Long botId,
        ScheduledJobType jobType,
        long runAtNanos
) implements Delayed {

    public static ScheduledBotTask afterDelay(
            Long botId,
            ScheduledJobType jobType,
            long delayMillis
    ) {

        long safeDelayMillis =
                Math.max(
                        0L,
                        delayMillis
                );

        return new ScheduledBotTask(
                botId,
                jobType,
                System.nanoTime()
                        + TimeUnit.MILLISECONDS.toNanos(
                        safeDelayMillis
                )
        );
    }

    @Override
    public long getDelay(
            TimeUnit unit
    ) {

        long remainingNanos =
                runAtNanos
                        - System.nanoTime();

        return unit.convert(
                remainingNanos,
                TimeUnit.NANOSECONDS
        );
    }

    @Override
    public int compareTo(
            Delayed other
    ) {

        if (other == this) {
            return 0;
        }

        long difference =
                getDelay(TimeUnit.NANOSECONDS)
                        - other.getDelay(TimeUnit.NANOSECONDS);

        if (difference != 0L) {
            return Long.compare(difference, 0L);
        }

        if (other instanceof ScheduledBotTask otherTask) {
            int botComparison =
                    Long.compare(
                            botId,
                            otherTask.botId
                    );

            if (botComparison != 0) {
                return botComparison;
            }

            return jobType.compareTo(otherTask.jobType);
        }

        return 0;
    }
}
