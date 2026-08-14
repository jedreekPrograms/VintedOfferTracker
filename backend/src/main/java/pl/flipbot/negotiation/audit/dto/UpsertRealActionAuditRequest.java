package pl.flipbot.negotiation.audit.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import pl.flipbot.negotiation.audit.RealActionAuditOutcome;
import pl.flipbot.negotiation.audit.RealActionMessageStatus;
import pl.flipbot.negotiation.guard.RealActionType;

import java.math.BigDecimal;
import java.util.UUID;

public record UpsertRealActionAuditRequest(
        @NotNull UUID requestId,
        @NotNull RealActionType actionType,
        @NotNull @Positive Integer stepNumber,
        @DecimalMin("0.01") BigDecimal offerPrice,
        @NotNull RealActionAuditOutcome outcome,
        @NotNull RealActionMessageStatus messageStatus,
        @Size(max = 1000) String failureReason
) {
}
