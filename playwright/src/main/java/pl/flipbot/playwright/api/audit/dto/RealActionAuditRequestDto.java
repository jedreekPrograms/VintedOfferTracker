package pl.flipbot.playwright.api.audit.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record RealActionAuditRequestDto(
        UUID requestId,
        String actionType,
        Integer stepNumber,
        BigDecimal offerPrice,
        String outcome,
        String messageStatus,
        String failureReason
) {
}
