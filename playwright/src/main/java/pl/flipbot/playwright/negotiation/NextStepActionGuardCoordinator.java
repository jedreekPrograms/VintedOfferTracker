package pl.flipbot.playwright.negotiation;

import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.guard.RealActionGuardClient;
import pl.flipbot.playwright.api.guard.dto.RealActionGuardResponseDto;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.model.NegotiationStepDto;

import java.util.Objects;
import java.util.UUID;

@Slf4j
public class NextStepActionGuardCoordinator {

    private static final String ACTION_TYPE = "NEXT_STEP";

    private final RealActionGuardClient realActionGuardClient =
            new RealActionGuardClient();

    public UUID acquire(
            Long botId,
            ListingResponseDto listing,
            NegotiationStepDto nextStep
    ) {
        Objects.requireNonNull(botId, "Bot id cannot be null");
        Objects.requireNonNull(listing, "Listing cannot be null");
        Objects.requireNonNull(listing.id(), "Backend listing id cannot be null");
        Objects.requireNonNull(nextStep, "Next negotiation step cannot be null");
        Objects.requireNonNull(nextStep.getStepNumber(), "Next step number cannot be null");

        UUID requestId = UUID.randomUUID();

        RealActionGuardResponseDto response =
                realActionGuardClient.acquire(
                        botId,
                        listing.id(),
                        ACTION_TYPE,
                        nextStep.getStepNumber(),
                        requestId
                );

        if (!response.acquired()) {
            log.error(
                    "[REAL ACTION GUARD] NEXT_STEP blocked for bot {}, backend listing {}, marketplace listing {}, step {}. "
                            + "No quota will be reserved and no real submit will be attempted. Existing action={}, step={}, createdAt={}.",
                    botId,
                    listing.id(),
                    listing.listingId(),
                    nextStep.getStepNumber(),
                    response.actionType(),
                    response.stepNumber(),
                    response.createdAt()
            );

            return null;
        }

        if (!Objects.equals(requestId, response.requestId())) {
            throw new IllegalStateException(
                    "Backend acquired NEXT_STEP guard with an unexpected requestId for listing "
                            + listing.id()
            );
        }

        log.warn(
                "[REAL ACTION GUARD] NEXT_STEP guard acquired for bot {}, backend listing {}, marketplace listing {}, step {}, requestId={}.",
                botId,
                listing.id(),
                listing.listingId(),
                nextStep.getStepNumber(),
                requestId
        );

        return requestId;
    }

    public void releaseBeforeSubmitOrRetrySafely(
            Long botId,
            ListingResponseDto listing,
            UUID requestId,
            String reason
    ) {
        if (requestId == null) {
            return;
        }

        try {
            realActionGuardClient.release(
                    botId,
                    listing.id(),
                    requestId
            );

            log.info(
                    "[REAL ACTION GUARD] NEXT_STEP guard released before confirmed real delivery for bot {}, marketplace listing {}. Reason: {}.",
                    botId,
                    listing.listingId(),
                    reason
            );

        } catch (Exception exception) {
            log.error(
                    "[REAL ACTION GUARD] Could not release NEXT_STEP guard before a safe retry for bot {}, marketplace listing {}. "
                            + "Failing closed. Reason: {}. Error: {}",
                    botId,
                    listing.listingId(),
                    reason,
                    friendlyMessage(exception)
            );

            throw new IllegalStateException(
                    "Could not release NEXT_STEP real-action guard safely for listing "
                            + listing.id(),
                    exception
            );
        }
    }

    public void releaseAfterConfirmedSuccessBestEffort(
            Long botId,
            ListingResponseDto listing,
            UUID requestId
    ) {
        if (requestId == null) {
            return;
        }

        try {
            realActionGuardClient.release(
                    botId,
                    listing.id(),
                    requestId
            );

            log.info(
                    "[REAL ACTION GUARD] NEXT_STEP guard resolved after confirmed backend success for bot {}, marketplace listing {}.",
                    botId,
                    listing.listingId()
            );

        } catch (Exception exception) {
            log.warn(
                    "[REAL ACTION GUARD] NEXT_STEP succeeded, but guard cleanup failed for bot {}, marketplace listing {}. "
                            + "The stale guard is safe: backend listing state can confirm the step on a later acquire. Error: {}",
                    botId,
                    listing.listingId(),
                    friendlyMessage(exception)
            );

            log.trace(
                    "[REAL ACTION GUARD] Full NEXT_STEP post-success cleanup error for listing {}.",
                    listing.id(),
                    exception
            );
        }
    }

    private String friendlyMessage(Throwable exception) {
        if (exception == null) {
            return "Unknown error";
        }

        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message.lines().findFirst().orElse(message).trim();
    }
}
