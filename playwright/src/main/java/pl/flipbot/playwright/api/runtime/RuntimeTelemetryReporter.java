package pl.flipbot.playwright.api.runtime;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
public class RuntimeTelemetryReporter implements AutoCloseable {

    private static final long SYNCHRONOUS_EVENT_TIMEOUT_SECONDS = 10L;
    private static final int MAX_PENDING_EVENTS = 256;

    private final RuntimeTelemetryClient client = new RuntimeTelemetryClient();

    private final ThreadPoolExecutor executor = createExecutor();

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
        return await(
                submit(() -> client.getState(botId)),
                "read runtime state for bot " + botId
        );
    }

    /**
     * Session blocking is scheduler-significant: the backend owns the
     * persistent attempt counter and calculates the next exponential retry.
     * This method therefore waits for the response on the SAME single-threaded
     * telemetry executor. Any earlier accepted event remains FIFO-ordered before
     * this call. If the bounded telemetry queue is saturated, submit() fails
     * explicitly and BotWorkerSlot uses its existing conservative fallback
     * cooldown instead of letting telemetry consume unbounded memory.
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

        RuntimeTelemetryStateResponse response = await(
                submit(() -> client.sendEvent(botId, request)),
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
        try {
            executor.execute(() -> sendNow(botId, request));
        } catch (RejectedExecutionException exception) {
            if (executor.isShutdown()) {
                log.debug(
                        "[TELEMETRY] Reporter is already shutting down. Dropping {} for bot {}.",
                        request.eventType(),
                        botId
                );
                return;
            }

            log.warn(
                    "[TELEMETRY] Pending telemetry queue reached its hard limit of {} event(s). "
                            + "Dropping {} for bot {} instead of allowing unbounded memory growth. "
                            + "Scheduler work continues.",
                    MAX_PENDING_EVENTS,
                    request.eventType(),
                    botId
            );
        }
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

    private <T> Future<T> submit(java.util.concurrent.Callable<T> callable) {
        try {
            return executor.submit(callable);
        } catch (RejectedExecutionException exception) {
            String reason = executor.isShutdown()
                    ? "Runtime telemetry reporter is shutting down."
                    : "Runtime telemetry queue is saturated at "
                            + MAX_PENDING_EVENTS
                            + " pending event(s).";

            throw new IllegalStateException(
                    reason,
                    exception
            );
        }
    }

    private <T> T await(Future<T> future, String operation) {
        try {
            return future.get(
                    SYNCHRONOUS_EVENT_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrupted while attempting to " + operation + ".",
                    exception
            );
        } catch (ExecutionException | TimeoutException exception) {
            future.cancel(true);
            throw new IllegalStateException(
                    "Could not " + operation + ".",
                    exception
            );
        }
    }

    @Override
    public void close() {
        executor.shutdown();

        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    static ThreadPoolExecutor createExecutor() {
        return new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(MAX_PENDING_EVENTS),
                runnable -> {
                    Thread thread = new Thread(
                            runnable,
                            "flipbot-runtime-telemetry"
                    );
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    static int maxPendingEvents() {
        return MAX_PENDING_EVENTS;
    }

    public record SessionBlockCooldown(
            long nextRunAtEpochMs,
            int attemptNumber,
            String blockedSince
    ) {
    }
}
