package pl.flipbot.playwright.api.runtime;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RuntimeTelemetryEventBufferTest {

    @Test
    public void consecutiveQueuedEventsAreCoalescedToNewestSchedule() {
        RuntimeTelemetryEventBuffer buffer = new RuntimeTelemetryEventBuffer();

        RuntimeTelemetryEventBuffer.OfferResult first = buffer.offer(
                event("QUEUED", 100L, null, null)
        );
        RuntimeTelemetryEventBuffer.OfferResult second = buffer.offer(
                event("QUEUED", 200L, null, null)
        );

        assertFalse(first.coalesced());
        assertTrue(second.coalesced());
        assertNull(second.dropped());
        assertEquals(1, buffer.size());

        RuntimeTelemetryEventRequest queued = buffer.poll();
        assertNotNull(queued);
        assertEquals("QUEUED", queued.eventType());
        assertEquals(Long.valueOf(200L), queued.nextRunAtEpochMs());
        assertTrue(buffer.isEmpty());
    }

    @Test
    public void hardLimitDropsOldestEventInsteadOfGrowingForever() {
        RuntimeTelemetryEventBuffer buffer = new RuntimeTelemetryEventBuffer();
        RuntimeTelemetryEventRequest lastDropped = null;

        int total = RuntimeTelemetryEventBuffer.MAX_EVENTS + 5;
        for (int index = 1; index <= total; index++) {
            RuntimeTelemetryEventBuffer.OfferResult result = buffer.offer(
                    event("RUN_STARTED", null, null, index)
            );
            if (result.dropped() != null) {
                lastDropped = result.dropped();
            }
        }

        assertEquals(RuntimeTelemetryEventBuffer.MAX_EVENTS, buffer.size());
        assertNotNull(lastDropped);
        assertEquals(Integer.valueOf(5), lastDropped.workerSlot());

        RuntimeTelemetryEventRequest oldestRetained = buffer.poll();
        assertNotNull(oldestRetained);
        assertEquals(Integer.valueOf(6), oldestRetained.workerSlot());
    }

    @Test
    public void terminalEventKeepsItsOrderBeforeFollowingQueuedState() {
        RuntimeTelemetryEventBuffer buffer = new RuntimeTelemetryEventBuffer();

        buffer.offer(event("RUN_SUCCEEDED", null, 123L, null));
        buffer.offer(event("QUEUED", 456L, null, null));

        assertEquals("RUN_SUCCEEDED", buffer.poll().eventType());
        assertEquals("QUEUED", buffer.poll().eventType());
        assertTrue(buffer.isEmpty());
    }

    private RuntimeTelemetryEventRequest event(
            String type,
            Long nextRunAt,
            Long duration,
            Integer workerSlot
    ) {
        return new RuntimeTelemetryEventRequest(
                type,
                nextRunAt,
                duration,
                workerSlot,
                null
        );
    }
}
