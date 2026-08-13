package pl.flipbot.playwright.negotiation;

import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.guard.RealActionGuardClient;
import pl.flipbot.playwright.api.guard.dto.RealActionGuardResponseDto;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;

import java.util.Objects;
import java.util.UUID;

@Slf4j
public class FirstOfferActionGuardCoordinator {

    private static final String ACTION_TYPE = "FIRST_OFFER";
    private static final int FIRST_STEP = 1;

    private final RealActionGuardClient realActionGuardClient =
            new RealActionGuardClient();

    public UUID acquire(
            Long botId,
            ListingResponseDto listing
    ) {
        Objects.requireNonNull(botId, "Bot id cannot be null");
        Objects.requireNonNull(listing, "Listing cannot be null");
        Objects.requireNonNull(listing.id(), "Backend listing id cannot be null");

        UUID requestId = UUID.randomUUID();

        RealActionGuardResponseDto response =
                realActionGuardClient.acquire(
                        botId,
                        listing.id(),
                        ACTION_TYPE,
                        FIRST_STEP,
                        requestId
                );

        if (!response.acquired()) {
            log.error(
                    "[REAL ACTION GUARD] FIRST_OFFER blocked for bot {}, backend listing {}, marketplace listing {}. "
                            + "No quota will be reserved and no real submit will be attempted. Existing action={}, step={}, createdAt={}.",
                    botId,
                    listing.id(),
                    listing.listingId(),
                    response.actionType(),
                    response.stepNumber(),
                    response.createdAt()
            );

            return null;
        }

        if (!Objects.equals(requestId, response.requestId())) {
            throw new IllegalStateException(
                    "Backend acquired FIRST_OFFER guard with an unexpected requestId for listing "
                            + listing.id()
            );
        }

        log.warn(
                "[REAL ACTION GUARD] FIRST_OFFER guard acquired for bot {}, backend listing {}, marketplace listing {}, requestId={}.",
                botId,
                listing.id(),
                listing.listingId(),
                requestId
        );

        return requestId;
    }

    public void releaseBeforeSubmitSafely(
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
                    "[REAL ACTION GUARD] FIRST_OFFER guard released before real submit for bot {}, marketplace listing {}. Reason: {}.",
                    botId,
                    listing.listingId(),
                    reason
            );

        } catch (Exception exception) {
            log.error(
                    "[REAL ACTION GUARD] Could not release FIRST_OFFER guard before real submit for bot {}, marketplace listing {}. "
                            + "Failing closed: the caller must not attempt the real submit. Reason: {}. Error: {}",
                    botId,
                    listing.listingId(),
                    reason,
                    friendlyMessage(exception)
            );

            throw new IllegalStateException(
                    "Could not release FIRST_OFFER action guard before real submit for listing "
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
                    "[REAL ACTION GUARD] FIRST_OFFER guard resolved after confirmed backend success for bot {}, marketplace listing {}.",
                    botId,
                    listing.listingId()
            );

        } catch (Exception exception) {
            log.warn(
                    "[REAL ACTION GUARD] FIRST_OFFER succeeded, but guard cleanup failed for bot {}, marketplace listing {}. "
                            + "The stale guard is safe: backend listing state can confirm the action on a later acquire. Error: {}",
                    botId,
                    listing.listingId(),
                    friendlyMessage(exception)
            );

            log.trace(
                    "[REAL ACTION GUARD] Full post-success cleanup error for listing {}.",
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
