package pl.flipbot.negotiation.audit.dto;

import pl.flipbot.negotiation.audit.RealActionAuditOutcome;
import pl.flipbot.negotiation.audit.RealActionMessageStatus;
import pl.flipbot.negotiation.guard.RealActionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record RealActionAuditResponse(
        Long id,
        UUID requestId,
        Long botId,
        Long backendListingId,
        String marketplaceListingId,
        String conversationId,
        RealActionType actionType,
        Integer stepNumber,
        BigDecimal offerPrice,
        RealActionAuditOutcome outcome,
        RealActionMessageStatus messageStatus,
        String failureReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
