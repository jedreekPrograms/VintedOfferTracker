package pl.flipbot.playwright.worker;

import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

public record ScheduledBotTask(
        Long botId,
        long runAtNanos
) implements Delayed {

    public static ScheduledBotTask now(
            Long botId
    ) {

        return new ScheduledBotTask(
                botId,
                System.nanoTime()
        );
    }


    public static ScheduledBotTask afterDelay(
            Long botId,
            long delayMillis
    ) {

        long safeDelayMillis =
                Math.max(
                        0L,
                        delayMillis
                );


        return new ScheduledBotTask(
                botId,
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

        if (
                other == this
        ) {

            return 0;
        }


        long difference =
                getDelay(
                        TimeUnit.NANOSECONDS
                )
                        - other.getDelay(
                        TimeUnit.NANOSECONDS
                );


        return Long.compare(
                difference,
                0L
        );
    }
}
