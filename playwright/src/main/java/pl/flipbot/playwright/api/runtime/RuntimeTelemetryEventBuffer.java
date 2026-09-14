package pl.flipbot.playwright.api.runtime;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Small per-bot buffer for best-effort runtime telemetry.
 *
 * <p>Runtime telemetry drives the dashboard, not marketplace correctness. It
 * must therefore never be allowed to grow without a bound while the backend is
 * slow or unavailable. Consecutive QUEUED updates are coalesced because only
 * the newest scheduled timestamp is useful.</p>
 */
final class RuntimeTelemetryEventBuffer {

    static final int MAX_EVENTS = 16;

    private final Deque<RuntimeTelemetryEventRequest> events =
            new ArrayDeque<>();

    OfferResult offer(RuntimeTelemetryEventRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Telemetry request is required");
        }

        RuntimeTelemetryEventRequest last = events.peekLast();
        if (isQueued(request) && isQueued(last)) {
            events.removeLast();
            events.addLast(request);
            return new OfferResult(null, true);
        }

        RuntimeTelemetryEventRequest dropped = null;
        if (events.size() >= MAX_EVENTS) {
            dropped = events.removeFirst();
        }

        events.addLast(request);
        return new OfferResult(dropped, false);
    }

    RuntimeTelemetryEventRequest poll() {
        return events.pollFirst();
    }

    int size() {
        return events.size();
    }

    boolean isEmpty() {
        return events.isEmpty();
    }

    void clear() {
        events.clear();
    }

    private boolean isQueued(RuntimeTelemetryEventRequest request) {
        return request != null && "QUEUED".equals(request.eventType());
    }

    record OfferResult(
            RuntimeTelemetryEventRequest dropped,
            boolean coalesced
    ) {
    }
}
