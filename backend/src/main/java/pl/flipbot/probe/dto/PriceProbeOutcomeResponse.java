package pl.flipbot.probe.dto;

import pl.flipbot.probe.PriceProbeStatus;

import java.time.LocalDateTime;

public record PriceProbeOutcomeResponse(
        Long probeId,
        PriceProbeStatus status,
        LocalDateTime completedAt
) {
}
