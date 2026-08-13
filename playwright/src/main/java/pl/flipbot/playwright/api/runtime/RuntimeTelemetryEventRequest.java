package pl.flipbot.playwright.api.runtime;

public record RuntimeTelemetryEventRequest(
        String eventType,
        Long nextRunAtEpochMs,
        Long durationMs,
        Integer workerSlot,
        String errorMessage
) {
}
