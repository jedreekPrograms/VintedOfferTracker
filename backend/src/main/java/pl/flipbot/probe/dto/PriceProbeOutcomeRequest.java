package pl.flipbot.probe.dto;

import jakarta.validation.constraints.NotNull;

public record PriceProbeOutcomeRequest(
        @NotNull PriceProbeOutcome outcome,
        String details
) {
}
