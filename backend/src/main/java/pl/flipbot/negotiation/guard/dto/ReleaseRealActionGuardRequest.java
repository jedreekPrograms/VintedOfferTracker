package pl.flipbot.negotiation.guard.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ReleaseRealActionGuardRequest(
        @NotNull UUID requestId
) {
}
