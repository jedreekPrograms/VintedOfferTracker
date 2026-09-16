package pl.flipbot.playwright.worker;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class ScheduledJobFailureBackoffPolicyTest {

    @Test
    public void catalogBackoffUsesTwoFiveTenAndFifteenMinutes() {
        long fallback = TimeUnit.SECONDS.toMillis(60L);

        assertEquals(
                TimeUnit.MINUTES.toMillis(2L),
                ScheduledJobFailureBackoffPolicy.delayMillis(
                        ScheduledJobType.CATALOG_SCAN,
                        1,
                        fallback
                )
        );
        assertEquals(
                TimeUnit.MINUTES.toMillis(5L),
                ScheduledJobFailureBackoffPolicy.delayMillis(
                        ScheduledJobType.CATALOG_SCAN,
                        2,
                        fallback
                )
        );
        assertEquals(
                TimeUnit.MINUTES.toMillis(10L),
                ScheduledJobFailureBackoffPolicy.delayMillis(
                        ScheduledJobType.CATALOG_SCAN,
                        3,
                        fallback
                )
        );
        assertEquals(
                TimeUnit.MINUTES.toMillis(15L),
                ScheduledJobFailureBackoffPolicy.delayMillis(
                        ScheduledJobType.CATALOG_SCAN,
                        4,
                        fallback
                )
        );
        assertEquals(
                TimeUnit.MINUTES.toMillis(15L),
                ScheduledJobFailureBackoffPolicy.delayMillis(
                        ScheduledJobType.CATALOG_SCAN,
                        20,
                        fallback
                )
        );
    }

    @Test
    public void negotiationBackoffIsIndependentAndMoreResponsive() {
        long fallback = TimeUnit.SECONDS.toMillis(60L);

        assertEquals(
                TimeUnit.MINUTES.toMillis(1L),
                ScheduledJobFailureBackoffPolicy.delayMillis(
                        ScheduledJobType.NEGOTIATION_CHECK,
                        1,
                        fallback
                )
        );
        assertEquals(
                TimeUnit.MINUTES.toMillis(2L),
                ScheduledJobFailureBackoffPolicy.delayMillis(
                        ScheduledJobType.NEGOTIATION_CHECK,
                        2,
                        fallback
                )
        );
        assertEquals(
                TimeUnit.MINUTES.toMillis(5L),
                ScheduledJobFailureBackoffPolicy.delayMillis(
                        ScheduledJobType.NEGOTIATION_CHECK,
                        3,
                        fallback
                )
        );
        assertEquals(
                TimeUnit.MINUTES.toMillis(10L),
                ScheduledJobFailureBackoffPolicy.delayMillis(
                        ScheduledJobType.NEGOTIATION_CHECK,
                        4,
                        fallback
                )
        );
    }

    @Test
    public void configuredFailureDelayActsAsMinimum() {
        long configured = TimeUnit.MINUTES.toMillis(20L);

        assertEquals(
                configured,
                ScheduledJobFailureBackoffPolicy.delayMillis(
                        ScheduledJobType.CATALOG_SCAN,
                        1,
                        configured
                )
        );
        assertEquals(
                configured,
                ScheduledJobFailureBackoffPolicy.delayMillis(
                        ScheduledJobType.NEGOTIATION_CHECK,
                        1,
                        configured
                )
        );
        assertEquals(
                configured,
                ScheduledJobFailureBackoffPolicy.delayMillis(
                        ScheduledJobType.PRICE_PROBE,
                        7,
                        configured
                )
        );
    }

    @Test
    public void rejectsInvalidFailureCount() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ScheduledJobFailureBackoffPolicy.delayMillis(
                        ScheduledJobType.CATALOG_SCAN,
                        0,
                        0L
                )
        );
    }
}
