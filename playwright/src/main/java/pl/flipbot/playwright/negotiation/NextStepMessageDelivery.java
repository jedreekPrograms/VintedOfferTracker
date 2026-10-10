package pl.flipbot.playwright.negotiation;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.model.NegotiationStepDto;
import pl.flipbot.playwright.verification.HumanVerificationHandler;

/**
 * Best-effort textual message AFTER a new price offer has been confirmed and
 * persisted. Errors are logged and intentionally never cause a price resubmit.
 * A missing message does not perform any chat interaction.
 */
@Slf4j
final class NextStepMessageDelivery {
    private static final double MESSAGE_TIMEOUT_MS = 20_000;
    private static final double MESSAGE_CONFIRMATION_TIMEOUT_MS = 5_000;
    private static final double MESSAGE_CONFIRMATION_POLL_INTERVAL_MS = 250;

    private final HumanVerificationHandler humanVerificationHandler;

    NextStepMessageDelivery(HumanVerificationHandler humanVerificationHandler) {
        this.humanVerificationHandler = humanVerificationHandler;
    }

    void sendMessageSafely(
            Page page,
            ListingResponseDto listing,
            NegotiationStepDto nextStep
    ) {

        String message =
                nextStep.getMessage();

        if (message == null
                || message.isBlank()) {

            log.info(
                    "[NEXT STEP REAL] Step {} has no configured message. "
                            + "Only the price offer was sent for listing {}.",
                    nextStep.getStepNumber(),
                    listing.listingId()
            );

            return;

        }

        try {

            humanVerificationHandler.waitUntilVerified(
                    page
            );

            Locator messageInput = NegotiationMessageComposer.fillAndVerify(
                    page, message, MESSAGE_TIMEOUT_MS, "Chat input contains an unexpected message"
            );
            Locator sendButton = NegotiationMessageComposer.requireSendButton(
                    page, MESSAGE_TIMEOUT_MS
            );

            log.info(
                    "[NEXT STEP REAL] Sending message for step {} "
                            + "and marketplace listing {}.",
                    nextStep.getStepNumber(),
                    listing.listingId()
            );

            sendButton.click(
                    new Locator.ClickOptions()
                            .setTimeout(
                                    MESSAGE_TIMEOUT_MS
                            )
            );

            boolean composerCleared =
                    NegotiationMessageComposer.awaitClear(
                            page, messageInput, MESSAGE_CONFIRMATION_TIMEOUT_MS,
                            MESSAGE_CONFIRMATION_POLL_INTERVAL_MS
                    );

            if (composerCleared) {

                log.info(
                        "[NEXT STEP REAL] Message for step {} was sent "
                                + "for marketplace listing {}.",
                        nextStep.getStepNumber(),
                        listing.listingId()
                );

            } else {

                log.warn(
                        "[NEXT STEP REAL] Send button was clicked, but "
                                + "the message input did not clear. "
                                + "The message may require manual verification. "
                                + "Marketplace listing: {}",
                        listing.listingId()
                );

            }

        } catch (Exception exception) {

            /*
             * Oferta została już wysłana i backend został zaktualizowany.
             * Błąd wiadomości nie może spowodować ponownego wysłania ceny.
             */
            log.error(
                    "[NEXT STEP REAL] The price offer was sent and backend "
                            + "was updated, but the message for step {} "
                            + "could not be sent. Marketplace listing: {}",
                    nextStep.getStepNumber(),
                    listing.listingId(),
                    exception
            );

        }

    }
}
