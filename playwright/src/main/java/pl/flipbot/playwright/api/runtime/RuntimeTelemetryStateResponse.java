package pl.flipbot.playwright.api.runtime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RuntimeTelemetryStateResponse(
        String nextRunAt,
        Integer sessionBlockCount,
        String sessionBlockedSince
) {
}
