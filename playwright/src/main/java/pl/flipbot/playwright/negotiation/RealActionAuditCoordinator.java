package pl.flipbot.playwright.negotiation;

import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.audit.RealActionAuditClient;
import pl.flipbot.playwright.api.audit.dto.RealActionAuditRequestDto;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.context.BotContext;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

@Slf4j
public class RealActionAuditCoordinator {

    private static final String OUTCOME_CONFIRMED = "CONFIRMED";
    private static final String OUTCOME_AMBIGUOUS = "AMBIGUOUS";
    private static final String MESSAGE_UNKNOWN = "UNKNOWN";

    private final BotContext context;
    private final RealActionAuditClient auditClient;

    public RealActionAuditCoordinator(
            BotContext context
    ) {
        this(
                context,
                new RealActionAuditClient()
        );
    }

    RealActionAuditCoordinator(
            BotContext context,
            RealActionAuditClient auditClient
    ) {
        this.context = Objects.requireNonNull(
                context,
                "Bot context cannot be null"
        );
        this.auditClient = Objects.requireNonNull(
                auditClient,
                "Audit client cannot be null"
        );
    }

    public void recordConfirmedRequired(
            ListingResponseDto listing,
            String actionType,
            Integer stepNumber,
            BigDecimal offerPrice,
            UUID requestId
    ) {
        record(
                listing,
                actionType,
                stepNumber,
                offerPrice,
                requestId,
                OUTCOME_CONFIRMED,
                MESSAGE_UNKNOWN,
                null
        );
    }

    public void recordAmbiguousBestEffort(
            ListingResponseDto listing,
            String actionType,
            Integer stepNumber,
            BigDecimal offerPrice,
            UUID requestId,
            Throwable failure
    ) {
        if (requestId == null) {
            return;
        }

        try {
            record(
                    listing,
                    actionType,
                    stepNumber,
                    offerPrice,
                    requestId,
                    OUTCOME_AMBIGUOUS,
                    MESSAGE_UNKNOWN,
                    friendlyMessage(failure)
            );
        } catch (Exception auditFailure) {
            log.error(
                    "[REAL ACTION AUDIT] Could not persist AMBIGUOUS audit for bot {}, marketplace listing {}, action {}, step {}, requestId={}. Persistent action guard remains fail-closed. Audit error: {}",
                    context.getBot().getId(),
                    listing.listingId(),
                    actionType,
                    stepNumber,
                    requestId,
                    friendlyMessage(auditFailure)
            );
            log.trace(
                    "[REAL ACTION AUDIT] Full ambiguous-audit persistence failure.",
                    auditFailure
            );
        }
    }

    private void record(
            ListingResponseDto listing,
            String actionType,
            Integer stepNumber,
            BigDecimal offerPrice,
            UUID requestId,
            String outcome,
            String messageStatus,
            String failureReason
    ) {
        Objects.requireNonNull(listing, "Listing cannot be null");
        Objects.requireNonNull(listing.id(), "Backend listing id cannot be null");
        Objects.requireNonNull(actionType, "Action type cannot be null");
        Objects.requireNonNull(stepNumber, "Step number cannot be null");
        Objects.requireNonNull(requestId, "Request id cannot be null");

        Long botId = Objects.requireNonNull(
                context.getBot().getId(),
                "Bot id cannot be null"
        );

        BigDecimal verifiedOfferPrice =
                requirePositiveOfferPrice(offerPrice);

        auditClient.record(
                botId,
                listing.id(),
                new RealActionAuditRequestDto(
                        requestId,
                        actionType,
                        stepNumber,
                        verifiedOfferPrice,
                        outcome,
                        messageStatus,
                        failureReason
                )
        );
    }

    private BigDecimal requirePositiveOfferPrice(
            BigDecimal offerPrice
    ) {
        if (offerPrice == null || offerPrice.signum() <= 0) {
            throw new IllegalStateException(
                    "Effective offer price must be positive for real-action audit"
            );
        }
        return offerPrice;
    }

    private String friendlyMessage(
            Throwable exception
    ) {
        if (exception == null) {
            return "Unknown post-submit failure";
        }

        String message = exception.getMessage();
        String normalized = message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message.lines().findFirst().orElse(message).trim();

        return normalized.length() <= 1000
                ? normalized
                : normalized.substring(0, 1000);
    }
}
