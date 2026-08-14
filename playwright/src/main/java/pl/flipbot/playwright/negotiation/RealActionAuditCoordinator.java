package pl.flipbot.playwright.negotiation;

import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.audit.RealActionAuditClient;
import pl.flipbot.playwright.api.audit.dto.RealActionAuditRequestDto;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.model.NegotiationStepDto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
public class RealActionAuditCoordinator {

    private static final String OUTCOME_CONFIRMED = "CONFIRMED";
    private static final String OUTCOME_AMBIGUOUS = "AMBIGUOUS";
    private static final String MESSAGE_UNKNOWN = "UNKNOWN";

    private final BotContext context;
    private final RealActionAuditClient auditClient =
            new RealActionAuditClient();

    public RealActionAuditCoordinator(
            BotContext context
    ) {
        this.context = Objects.requireNonNull(
                context,
                "Bot context cannot be null"
        );
    }

    public void recordConfirmedRequired(
            ListingResponseDto listing,
            String actionType,
            Integer stepNumber,
            UUID requestId
    ) {
        record(
                listing,
                actionType,
                stepNumber,
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

        BigDecimal offerPrice = resolveOfferPrice(stepNumber);

        auditClient.record(
                botId,
                listing.id(),
                new RealActionAuditRequestDto(
                        requestId,
                        actionType,
                        stepNumber,
                        offerPrice,
                        outcome,
                        messageStatus,
                        failureReason
                )
        );
    }

    private BigDecimal resolveOfferPrice(
            Integer stepNumber
    ) {
        if (context.getBot().getConfiguration() == null) {
            throw new IllegalStateException("Bot configuration is missing");
        }

        List<NegotiationStepDto> steps =
                context.getBot().getConfiguration().getNegotiationSteps();

        if (steps == null) {
            throw new IllegalStateException("Negotiation steps are missing");
        }

        return steps.stream()
                .filter(Objects::nonNull)
                .filter(step -> Objects.equals(
                        stepNumber,
                        step.getStepNumber()
                ))
                .map(NegotiationStepDto::getOfferPrice)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(
                        () -> new IllegalStateException(
                                "Could not resolve configured offer price for negotiation step "
                                        + stepNumber
                        )
                );
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
