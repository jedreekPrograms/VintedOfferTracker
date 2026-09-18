package pl.flipbot.playwright.worker;

import org.junit.Test;
import pl.flipbot.playwright.api.runtime.RuntimeTelemetryReporter;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class BotRunSchedulerFailureReportingTest {

    @Test
    public void reportsOneFailureWithActualRetryAndResetsOnlyAfterSuccess() throws Exception {
        try (RecordingTelemetry telemetry = new RecordingTelemetry()) {
            MutableClock clock = new MutableClock();
            BotRunScheduler scheduler = scheduler(telemetry, clock);
            scheduler.reconcileRunningBots(Map.of(1L, false));
            assertEquals(ScheduledJobType.CATALOG_SCAN, scheduler.pollNext(0L).jobType());
            telemetry.events.clear();

            scheduler.completeFailedRun(1L, ScheduledJobType.CATALOG_SCAN, minutes(1), 123L, "render failed");

            assertEquals(List.of("FAILED"), telemetry.events);
            assertEquals(clock.millis() + minutes(2), telemetry.nextRunAt);
            assertEquals(123L, telemetry.duration);
            assertEquals("render failed", telemetry.error);
            assertNull(scheduler.pollNext(0L));
            clock.advance(minutes(2));
            assertEquals(ScheduledJobType.CATALOG_SCAN, scheduler.pollNext(0L).jobType());
            scheduler.completeFailedRun(1L, ScheduledJobType.CATALOG_SCAN, minutes(1), 456L, "again");
            assertEquals(clock.millis() + minutes(5), telemetry.nextRunAt);

            clock.advance(minutes(5));
            assertNotNull(scheduler.pollNext(0L));
            scheduler.completeRun(1L, ScheduledJobType.CATALOG_SCAN, minutes(15), false, true);
            clock.advance(minutes(15));
            assertNotNull(scheduler.pollNext(0L));
            scheduler.completeFailedRun(1L, ScheduledJobType.CATALOG_SCAN, minutes(1), 1L, "new episode");
            assertEquals(clock.millis() + minutes(2), telemetry.nextRunAt);
        }
    }

    @Test
    public void localCapacityDeferralDoesNotResetCatalogFailureBackoff() throws Exception {
        try (RecordingTelemetry telemetry = new RecordingTelemetry()) {
            MutableClock clock = new MutableClock();
            BotRunScheduler scheduler = scheduler(telemetry, clock);

            scheduler.reconcileRunningBots(Map.of(1L, false));
            assertEquals(ScheduledJobType.CATALOG_SCAN, scheduler.pollNext(0L).jobType());

            scheduler.completeFailedRun(
                    1L,
                    ScheduledJobType.CATALOG_SCAN,
                    minutes(1),
                    1L,
                    "first failure"
            );

            clock.advance(minutes(2));
            assertEquals(ScheduledJobType.CATALOG_SCAN, scheduler.pollNext(0L).jobType());

            scheduler.deferRunForCapacity(
                    1L,
                    ScheduledJobType.CATALOG_SCAN,
                    5_000L
            );

            clock.advance(5_000L);
            assertEquals(ScheduledJobType.CATALOG_SCAN, scheduler.pollNext(0L).jobType());

            scheduler.completeFailedRun(
                    1L,
                    ScheduledJobType.CATALOG_SCAN,
                    minutes(1),
                    1L,
                    "second failure"
            );

            assertEquals(
                    clock.millis() + minutes(5),
                    telemetry.nextRunAt
            );
        }
    }

    @Test
    public void successfulNegotiationDoesNotResetCatalogBackoff() throws Exception {
        try (RecordingTelemetry telemetry = new RecordingTelemetry()) {
            MutableClock clock = new MutableClock();
            BotRunScheduler scheduler = scheduler(telemetry, clock);
            scheduler.reconcileRunningBots(Map.of(1L, false));
            assertNotNull(scheduler.pollNext(0L));
            scheduler.completeFailedRun(1L, ScheduledJobType.CATALOG_SCAN, minutes(1), 1L, "catalog");

            scheduler.reconcileRunningBots(Map.of(1L, true));
            assertEquals(ScheduledJobType.NEGOTIATION_CHECK, scheduler.pollNext(0L).jobType());
            scheduler.completeRun(1L, ScheduledJobType.NEGOTIATION_CHECK, minutes(5), false, true);
            scheduler.reconcileRunningBots(Map.of(1L, false));
            clock.advance(minutes(2));
            assertEquals(ScheduledJobType.CATALOG_SCAN, scheduler.pollNext(0L).jobType());
            scheduler.completeFailedRun(1L, ScheduledJobType.CATALOG_SCAN, minutes(1), 1L, "catalog again");

            assertEquals(clock.millis() + minutes(5), telemetry.nextRunAt);
        }
    }

    @Test
    public void botWideCooldownDoesNotCountAsOrdinaryFailure() throws Exception {
        try (RecordingTelemetry telemetry = new RecordingTelemetry()) {
            MutableClock clock = new MutableClock();
            BotRunScheduler scheduler = scheduler(telemetry, clock);
            scheduler.reconcileRunningBots(Map.of(1L, false));
            assertNotNull(scheduler.pollNext(0L));
            scheduler.completeRun(1L, ScheduledJobType.CATALOG_SCAN, minutes(10), true, false);
            clock.advance(minutes(10));
            assertNotNull(scheduler.pollNext(0L));
            scheduler.completeFailedRun(1L, ScheduledJobType.CATALOG_SCAN, minutes(1), 1L, "ordinary failure");
            assertEquals(clock.millis() + minutes(2), telemetry.nextRunAt);
        }
    }

    @Test
    public void stoppedBotFinishesIdleWithoutPublishingAStaleFailure() throws Exception {
        try (RecordingTelemetry telemetry = new RecordingTelemetry()) {
            MutableClock clock = new MutableClock();
            BotRunScheduler scheduler = scheduler(telemetry, clock);
            scheduler.reconcileRunningBots(Map.of(1L, false));
            assertNotNull(scheduler.pollNext(0L));
            scheduler.reconcileRunningBots(Map.of());
            telemetry.events.clear();
            scheduler.completeFailedRun(1L, ScheduledJobType.CATALOG_SCAN, minutes(1), 1L, "stopped");
            assertEquals(List.of("IDLE"), telemetry.events);
            assertEquals(0, scheduler.workingCount());
            assertNull(scheduler.pollNext(0L));
        }
    }

    private static BotRunScheduler scheduler(RecordingTelemetry telemetry, Clock clock) {
        WorkerRuntimeConfig config = new WorkerRuntimeConfig(
                1, 5L, 120L, 900L, 60L, 60L, 600L, 180L, 30L, true
        );
        return new BotRunScheduler(config, telemetry, new CatalogConcurrencyConfig(3, 1_000L), clock);
    }

    private static long minutes(long value) {
        return TimeUnit.MINUTES.toMillis(value);
    }

    private static final class MutableClock extends Clock {
        private long now = Instant.parse("2026-09-16T12:00:00Z").toEpochMilli();
        void advance(long millis) { now += millis; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(now); }
        @Override public long millis() { return now; }
    }

    private static final class RecordingTelemetry extends RuntimeTelemetryReporter {
        private final List<String> events = new ArrayList<>();
        private long nextRunAt;
        private long duration;
        private String error;
        @Override public void queued(Long botId, long nextRunAt) {
            events.add("QUEUED");
            this.nextRunAt = nextRunAt;
        }
        @Override public void idle(Long botId) { events.add("IDLE"); }
        @Override public void runFailed(Long botId, long duration, long nextRunAt, String error) {
            events.add("FAILED");
            this.duration = duration;
            this.nextRunAt = nextRunAt;
            this.error = error;
        }
    }
}
