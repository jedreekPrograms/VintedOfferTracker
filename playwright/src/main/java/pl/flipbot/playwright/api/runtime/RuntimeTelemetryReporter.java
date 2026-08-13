package pl.flipbot.playwright.api.runtime;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

@Slf4j
public class RuntimeTelemetryReporter implements AutoCloseable {

    private final RuntimeTelemetryClient client =
            new RuntimeTelemetryClient();

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor(
                    runnable -> {
                        Thread thread = new Thread(
                                runnable,
                                "flipbot-runtime-telemetry"
                        );
                        thread.setDaemon(true);
                        return thread;
                    }
            );

    public void queued(
            Long botId,
            long nextRunAtEpochMs
    ) {
        send(
                botId,
                new RuntimeTelemetryEventRequest(
                        "QUEUED",
                        nextRunAtEpochMs,
                        null,
                        null,
                        null
                )
        );
    }

    public void runStarted(
            Long botId,
            int workerSlot
    ) {
        send(
                botId,
                new RuntimeTelemetryEventRequest(
                        "RUN_STARTED",
                        null,
                        null,
                        workerSlot,
                        null
                )
        );
    }

    public void runSucceeded(
            Long botId,
            long durationMs
    ) {
        send(
                botId,
                new RuntimeTelemetryEventRequest(
                        "RUN_SUCCEEDED",
                        null,
                        durationMs,
                        null,
                        null
                )
        );
    }

    public void runFailed(
            Long botId,
            long durationMs,
            String errorMessage
    ) {
        send(
                botId,
                new RuntimeTelemetryEventRequest(
                        "RUN_FAILED",
                        null,
                        durationMs,
                        null,
                        errorMessage
                )
        );
    }

    public void rateLimited(
            Long botId,
            long durationMs,
            long nextRunAtEpochMs,
            String errorMessage
    ) {
        send(
                botId,
                new RuntimeTelemetryEventRequest(
                        "RATE_LIMITED",
                        nextRunAtEpochMs,
                        durationMs,
                        null,
                        errorMessage
                )
        );
    }

    public void idle(Long botId) {
        send(
                botId,
                new RuntimeTelemetryEventRequest(
                        "IDLE",
                        null,
                        null,
                        null,
                        null
                )
        );
    }

    private void send(
            Long botId,
            RuntimeTelemetryEventRequest request
    ) {
        try {
            executor.execute(
                    () -> sendNow(botId, request)
            );
        } catch (RejectedExecutionException exception) {
            log.debug(
                    "[TELEMETRY] Reporter is already shutting down. Dropping {} for bot {}.",
                    request.eventType(),
                    botId
            );
        }
    }

    private void sendNow(
            Long botId,
            RuntimeTelemetryEventRequest request
    ) {
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
}
