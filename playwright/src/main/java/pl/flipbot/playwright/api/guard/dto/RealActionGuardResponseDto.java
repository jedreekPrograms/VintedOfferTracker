package pl.flipbot.playwright.api.guard.dto;

import java.util.UUID;

public record RealActionGuardResponseDto(
        boolean acquired,
        boolean replayed,
        UUID requestId,
        String actionType,
        Integer stepNumber,
        String createdAt
) {
}
