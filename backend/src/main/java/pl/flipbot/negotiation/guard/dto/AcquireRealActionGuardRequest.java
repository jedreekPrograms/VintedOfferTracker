package pl.flipbot.negotiation.guard.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import pl.flipbot.negotiation.guard.RealActionType;

import java.util.UUID;

public record AcquireRealActionGuardRequest(
        @NotNull UUID requestId,
        @NotNull RealActionType actionType,
        @NotNull @Positive Integer stepNumber
) {
}
