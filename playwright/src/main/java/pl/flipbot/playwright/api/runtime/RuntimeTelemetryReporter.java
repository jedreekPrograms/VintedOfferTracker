package pl.flipbot.playwright.api.runtime;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
public class RuntimeTelemetryReporter implements AutoCloseable {

    private static final long SYNCHRONOUS_EVENT_TIMEOUT_SECONDS = 10L;

    private final RuntimeTelemetryClient client = new RuntimeTelemetryClient();

    private final ExecutorService executor = Executors.newSingleThreadExecutor(
            runnable -> {
                Thread thread = new Thread(runnable, "flipbot-runtime-telemetry");
                thread.setDaemon(true);
                return thread;
            }
    );

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

    /**
     * Session blocking is scheduler-significant: the backend owns the
     * persistent attempt counter and calculates the next exponential retry.
     * This method therefore waits for the response on the SAME single-threaded
     * telemetry executor. Any earlier RUN_STARTED event is guaranteed to reach
     * the backend first, while the worker receives the authoritative retry time.
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

        final Future<RuntimeTelemetryStateResponse> future;
        try {
            future = executor.submit(() -> client.sendEvent(botId, request));
        } catch (RejectedExecutionException exception) {
            throw new IllegalStateException("Runtime telemetry reporter is shutting down.", exception);
        }

        try {
            RuntimeTelemetryStateResponse response = future.get(
                    SYNCHRONOUS_EVENT_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
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
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrupted while reporting Vinted session block.",
                    exception
            );
        } catch (ExecutionException | TimeoutException exception) {
            future.cancel(true);
            throw new IllegalStateException(
                    "Could not obtain persisted Vinted session-block cooldown.",
                    exception
            );
        }
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
            log.debug(
                    "[TELEMETRY] Reporter is already shutting down. Dropping {} for bot {}.",
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

    public record SessionBlockCooldown(
            long nextRunAtEpochMs,
            int attemptNumber,
            String blockedSince
    ) {
    }
}
