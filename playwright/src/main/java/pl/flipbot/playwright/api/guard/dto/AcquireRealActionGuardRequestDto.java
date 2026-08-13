package pl.flipbot.playwright.api.guard.dto;

import java.util.UUID;

public record AcquireRealActionGuardRequestDto(
        UUID requestId,
        String actionType,
        Integer stepNumber
) {
}
