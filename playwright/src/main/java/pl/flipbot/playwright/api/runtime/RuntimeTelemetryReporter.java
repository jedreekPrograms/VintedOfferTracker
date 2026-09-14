package pl.flipbot.playwright.api.runtime;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

@Slf4j
public class RuntimeTelemetryReporter implements AutoCloseable {

    private static final long SYNCHRONOUS_LOCK_TIMEOUT_SECONDS = 20L;

    private final RuntimeTelemetryClient client;
    private final ExecutorService executor;

    /**
     * Best-effort dashboard events are buffered per bot instead of submitting
     * one unbounded executor task per event. Each per-bot buffer has a hard
     * limit and coalesces consecutive QUEUED updates.
     */
    private final ConcurrentMap<Long, RuntimeTelemetryEventBuffer> pendingEvents =
            new ConcurrentHashMap<>();

    private final AtomicBoolean drainScheduled = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * The old single-thread executor serialized every backend telemetry call.
     * Keep that property explicitly: critical synchronous calls and the async
     * drain share this fair lock, so SESSION_BLOCKED cannot be overwritten by
     * an older async event that was removed from a buffer but not sent yet.
     */
    private final ReentrantLock transportLock = new ReentrantLock(true);

    public RuntimeTelemetryReporter() {
        this(new RuntimeTelemetryClient());
    }

    RuntimeTelemetryReporter(RuntimeTelemetryClient client) {
        if (client == null) {
            throw new IllegalArgumentException("Runtime telemetry client is required");
        }

        this.client = client;
        this.executor = Executors.newSingleThreadExecutor(
                runnable -> {
                    Thread thread = new Thread(
                            runnable,
                            "flipbot-runtime-telemetry"
                    );
                    thread.setDaemon(true);
                    return thread;
                }
        );
    }

    public void queued(Long botId, long nextRunAtEpochMs) {
        send(botId, new RuntimeTelemetryEventRequest(
                "QUEUED", nextRunAtEpochMs, null, null, null
        ));
    }

    public void runStarted(Long botId, int workerSlot) {
        send(botId, new RuntimeTelemetryEventRequest(
                "RUN_STARTED", null, null, workerSlot, null
        ));
    }

    public void runSucceeded(Long botId, long durationMs) {
        send(botId, new RuntimeTelemetryEventRequest(
                "RUN_SUCCEEDED", null, durationMs, null, null
        ));
    }

    public void runFailed(
            Long botId,
            long durationMs,
            long nextRunAtEpochMs,
            String errorMessage
    ) {
        send(botId, new RuntimeTelemetryEventRequest(
                "RUN_FAILED", nextRunAtEpochMs, durationMs, null, errorMessage
        ));
    }

    public void rateLimited(
            Long botId,
            long durationMs,
            long nextRunAtEpochMs,
            String errorMessage
    ) {
        send(botId, new RuntimeTelemetryEventRequest(
                "RATE_LIMITED", nextRunAtEpochMs, durationMs, null, errorMessage
        ));
    }

    public RuntimeTelemetryStateResponse currentState(Long botId) {
        return withTransportLock(
                () -> client.getState(botId),
                "read runtime state for bot " + botId
        );
    }

    /**
     * Session blocking is scheduler-significant: the backend owns the
     * persistent attempt counter and calculates the next exponential retry.
     *
     * <p>This request bypasses the best-effort async buffer. While holding the
     * same transport lock as the drain, stale pending events for this bot are
     * discarded before SESSION_BLOCKED is persisted. An older QUEUED or
     * RUN_STARTED event therefore cannot arrive afterwards and overwrite the
     * authoritative COOLDOWN state.</p>
     */
    public SessionBlockCooldown sessionBlocked(
            Long botId,
            long durationMs,
            String errorMessage
    ) {
        RuntimeTelemetryEventRequest request = new RuntimeTelemetryEventRequest(
                "SESSION_BLOCKED",
                null,
                durationMs,
                null,
                errorMessage
        );

        RuntimeTelemetryStateResponse response = withTransportLock(
                () -> {
                    int discarded = clearPendingEvents(botId);
                    if (discarded > 0) {
                        log.debug(
                                "[TELEMETRY] Discarded {} stale pending event(s) for bot {} before authoritative SESSION_BLOCKED update.",
                                discarded,
                                botId
                        );
                    }
                    return client.sendEvent(botId, request);
                },
                "persist Vinted session block for bot " + botId
        );

        if (response == null || response.nextRunAt() == null) {
            throw new IllegalStateException(
                    "Backend did not return nextRunAt for SESSION_BLOCKED."
            );
        }

        long nextRunAtEpochMs = Instant.parse(response.nextRunAt()).toEpochMilli();
        int attemptNumber = response.sessionBlockCount() == null
                ? 1
                : Math.max(1, response.sessionBlockCount());

        return new SessionBlockCooldown(
                nextRunAtEpochMs,
                attemptNumber,
                response.sessionBlockedSince()
        );
    }

    public void idle(Long botId) {
        send(botId, new RuntimeTelemetryEventRequest(
                "IDLE", null, null, null, null
        ));
    }

    private void send(Long botId, RuntimeTelemetryEventRequest request) {
        if (botId == null || request == null) {
            return;
        }

        if (closed.get()) {
            log.debug(
                    "[TELEMETRY] Reporter is already shutting down. Dropping {} for bot {}.",
                    request.eventType(),
                    botId
            );
            return;
        }

        AtomicReference<RuntimeTelemetryEventBuffer.OfferResult> resultRef =
                new AtomicReference<>();

        pendingEvents.compute(
                botId,
                (ignored, existing) -> {
                    RuntimeTelemetryEventBuffer buffer = existing == null
                            ? new RuntimeTelemetryEventBuffer()
                            : existing;
                    resultRef.set(buffer.offer(request));
                    return buffer;
                }
        );

        RuntimeTelemetryEventBuffer.OfferResult result = resultRef.get();
        if (result != null && result.dropped() != null) {
            log.warn(
                    "[TELEMETRY] Per-bot telemetry buffer reached its hard limit of {}. Dropped oldest {} event for bot {} while keeping the newest state updates bounded in memory.",
                    RuntimeTelemetryEventBuffer.MAX_EVENTS,
                    result.dropped().eventType(),
                    botId
            );
        }

        scheduleDrain();
    }

    private void scheduleDrain() {
        if (pendingEvents.isEmpty()) {
            return;
        }

        if (!drainScheduled.compareAndSet(false, true)) {
            return;
        }

        try {
            executor.execute(this::drainPendingEvents);
        } catch (RejectedExecutionException exception) {
            drainScheduled.set(false);
            if (!closed.get()) {
                log.warn(
                        "[TELEMETRY] Could not schedule telemetry drain. Pending events remain bounded and will be retried by a later update.",
                        exception
                );
            }
        }
    }

    private void drainPendingEvents() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                if (!drainOneEvent()) {
                    return;
                }
            }
        } finally {
            drainScheduled.set(false);

            if (!pendingEvents.isEmpty()
                    && !executor.isShutdown()
                    && !Thread.currentThread().isInterrupted()) {
                scheduleDrain();
            }
        }
    }

    private boolean drainOneEvent() {
        try {
            transportLock.lockInterruptibly();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }

        try {
            PendingEvent pending = pollPendingEvent();
            if (pending == null) {
                return false;
            }

            sendNow(pending.botId(), pending.request());
            return true;
        } finally {
            transportLock.unlock();
        }
    }

    private PendingEvent pollPendingEvent() {
        for (Long botId : pendingEvents.keySet()) {
            AtomicReference<RuntimeTelemetryEventRequest> requestRef =
                    new AtomicReference<>();

            pendingEvents.computeIfPresent(
                    botId,
                    (ignored, buffer) -> {
                        RuntimeTelemetryEventRequest request = buffer.poll();
                        requestRef.set(request);
                        return buffer.isEmpty() ? null : buffer;
                    }
            );

            RuntimeTelemetryEventRequest request = requestRef.get();
            if (request != null) {
                return new PendingEvent(botId, request);
            }
        }

        return null;
    }

    private int clearPendingEvents(Long botId) {
        if (botId == null) {
            return 0;
        }

        AtomicReference<Integer> sizeRef = new AtomicReference<>(0);
        pendingEvents.computeIfPresent(
                botId,
                (ignored, buffer) -> {
                    sizeRef.set(buffer.size());
                    buffer.clear();
                    return null;
                }
        );
        return sizeRef.get();
    }

    private void sendNow(Long botId, RuntimeTelemetryEventRequest request) {
        try {
            client.sendEvent(botId, request);
        } catch (Exception exception) {
            log.warn(
                    "[TELEMETRY] Could not report {} for bot {}. Scheduler work continues.",
                    request.eventType(),
                    botId,
                    exception
            );
        }
    }

    private <T> T withTransportLock(
            Supplier<T> action,
            String operation
    ) {
        if (closed.get()) {
            throw new IllegalStateException(
                    "Runtime telemetry reporter is shutting down."
            );
        }

        boolean locked;
        try {
            locked = transportLock.tryLock(
                    SYNCHRONOUS_LOCK_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrupted while attempting to " + operation + ".",
                    exception
            );
        }

        if (!locked) {
            throw new IllegalStateException(
                    "Timed out waiting to " + operation + "."
            );
        }

        try {
            return action.get();
        } finally {
            transportLock.unlock();
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        /* Existing pending events still get one best-effort drain. No new
         * events may enter after closed=true. */
        scheduleDrain();
        executor.shutdown();

        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        } finally {
            pendingEvents.clear();
        }
    }

    int pendingEventCountForTests(Long botId) {
        RuntimeTelemetryEventBuffer buffer = pendingEvents.get(botId);
        return buffer == null ? 0 : buffer.size();
    }

    private record PendingEvent(
            Long botId,
            RuntimeTelemetryEventRequest request
    ) {
    }

    public record SessionBlockCooldown(
            long nextRunAtEpochMs,
            int attemptNumber,
            String blockedSince
    ) {
    }
}
