package pl.flipbot.playwright.api.runtime;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RuntimeTelemetryReporter {

    private final RuntimeTelemetryClient client =
            new RuntimeTelemetryClient();

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
}
