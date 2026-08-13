package pl.flipbot.negotiation.guard.dto;

import pl.flipbot.negotiation.guard.RealActionType;

import java.time.LocalDateTime;
import java.util.UUID;

public record RealActionGuardResponse(
        boolean acquired,
        boolean replayed,
        UUID requestId,
        RealActionType actionType,
        Integer stepNumber,
        LocalDateTime createdAt
) {
}
