package pl.flipbot.playwright.negotiation;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.listing.ListingClient;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.api.listing.dto.UpdateListingRequestDto;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.model.BotConfigurationDto;
import pl.flipbot.playwright.model.NegotiationStepDto;
import pl.flipbot.playwright.verification.HumanVerificationHandler;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
public class NextNegotiationStepExecutor {

    private static final double ELEMENT_TIMEOUT_MS =
            15_000;

    private static final double MESSAGE_TIMEOUT_MS =
            20_000;

    private static final double MESSAGE_CONFIRMATION_TIMEOUT_MS =
            5_000;

    private static final double MESSAGE_CONFIRMATION_POLL_INTERVAL_MS =
            250;

    private final BotContext context;

    /*
     * Dzięki temu obecny konstruktor:
     *
     * new NextNegotiationStepExecutor(context)
     *
     * nadal działa i nie musimy jeszcze zmieniać BotWorker.
     */
    private final ListingClient listingClient =
            new ListingClient();

    private final HumanVerificationHandler humanVerificationHandler =
            new HumanVerificationHandler();

    public NextStepPreparationResult prepareDryRun(
            ListingResponseDto listing,
            NegotiationStepDto nextStep
    ) {

        Objects.requireNonNull(
                listing,
                "Listing cannot be null"
        );

        Objects.requireNonNull(
                nextStep,
                "Next negotiation step cannot be null"
        );

        validateListing(
                listing
        );

        validateNextStep(
                listing,
                nextStep
        );

        Page page =
                context.getPage();

        log.info(
                "[NEXT STEP DRY RUN] Preparing step {} "
                        + "for backend listing {}, marketplace listing {}, "
                        + "offer price {}",
                nextStep.getStepNumber(),
                listing.id(),
                listing.listingId(),
                nextStep.getOfferPrice()
        );

        listing = new NextStepConversationReadiness(context, humanVerificationHandler).openConversation(
                page,
                listing,
                "[NEXT STEP DRY RUN]"
        );

        offerForm().openOfferModal(
                page,
                listing,
                "[NEXT STEP DRY RUN]"
        );

        boolean offerPrepared =
                offerForm().fillOfferPrice(
                        page,
                        listing,
                        nextStep,
                        "[NEXT STEP DRY RUN]"
                );

        if (!offerPrepared) {

            log.warn(
                    "[NEXT STEP DRY RUN] Step {} for marketplace listing {} "
                            + "cannot be sent because price {} is below "
                            + "the minimum allowed by Vinted. "
                            + "No offer was sent.",
                    nextStep.getStepNumber(),
                    listing.listingId(),
                    nextStep.getOfferPrice()
            );

            return NextStepPreparationResult.OFFER_TOO_LOW;

        }

        log.warn(
                "[NEXT STEP DRY RUN] Step {} for marketplace listing {} "
                        + "was prepared successfully. Offer price: {}. "
                        + "The submit button was NOT clicked.",
                nextStep.getStepNumber(),
                listing.listingId(),
                nextStep.getOfferPrice()
        );

        return NextStepPreparationResult.PREPARED;

    }

    public NextStepExecutionResult sendNextStep(
            ListingResponseDto listing,
            NegotiationStepDto nextStep
    ) {

        Objects.requireNonNull(
                listing,
                "Listing cannot be null"
        );

        Objects.requireNonNull(
                nextStep,
                "Next negotiation step cannot be null"
        );

        validateListing(
                listing
        );

        validateNextStep(
                listing,
                nextStep
        );

        Page page =
                context.getPage();

        log.warn(
                "[NEXT STEP REAL] Starting real negotiation step {} "
                        + "for backend listing {}, marketplace listing {}, "
                        + "configured offer price {}",
                nextStep.getStepNumber(),
                listing.id(),
                listing.listingId(),
                nextStep.getOfferPrice()
        );

        listing = new NextStepConversationReadiness(context, humanVerificationHandler).openConversation(
                page,
                listing,
                "[NEXT STEP REAL]"
        );

        offerForm().openOfferModal(
                page,
                listing,
                "[NEXT STEP REAL]"
        );

        boolean offerPrepared =
                offerForm().fillOfferPrice(
                        page,
                        listing,
                        nextStep,
                        "[NEXT STEP REAL]"
                );

        if (!offerPrepared) {

            log.warn(
                    "[NEXT STEP REAL] Step {} for marketplace listing {} "
                            + "was not sent because price {} is below "
                            + "the minimum allowed by Vinted.",
                    nextStep.getStepNumber(),
                    listing.listingId(),
                    nextStep.getOfferPrice()
            );

            return NextStepExecutionResult.OFFER_TOO_LOW;

        }

        /*
         * Zapamiętujemy liczbę naszych ofert przed kliknięciem.
         * Po wysłaniu w DOM powinien pojawić się kolejny element:
         *
         * offer-request-current-price-label
         */
        int ownOfferCountBefore =
                page.getByTestId(
                                NegotiationSelectors.OWN_OFFER_PRICE
                        )
                        .count();

        submitOffer(
                page,
                listing,
                nextStep
        );

        NextStepOwnOfferConfirmation.SubmittedOffer submittedOffer =
                new NextStepOwnOfferConfirmation(humanVerificationHandler).waitForNewOwnOffer(
                        page,
                        listing,
                        nextStep,
                        ownOfferCountBefore
                );

        /*
         * Najpierw zapisujemy kolejny krok w backendzie.
         *
         * Dopiero potem wysyłamy wiadomość tekstową. Dzięki temu błąd
         * wiadomości nie spowoduje ponownego wysłania tej samej oferty.
         */
        ListingResponseDto updatedListing =
                markNextStepStarted(
                        listing,
                        nextStep,
                        submittedOffer.displayedPrice()
                );

        log.info(
                "[NEXT STEP REAL] Backend listing {} was updated. "
                        + "Status: {}, step: {}, current price: {}, "
                        + "awaiting seller response: {}",
                updatedListing.id(),
                updatedListing.status(),
                updatedListing.currentStep(),
                updatedListing.currentPrice(),
                updatedListing.awaitingSellerResponse()
        );

        sendMessageSafely(
                page,
                listing,
                nextStep
        );

        log.warn(
                "[NEXT STEP REAL] Real negotiation step {} was sent "
                        + "for marketplace listing {}. "
                        + "Displayed price: {}, Vinted status: {}",
                nextStep.getStepNumber(),
                listing.listingId(),
                submittedOffer.displayedPrice(),
                submittedOffer.rawStatus()
        );

        return NextStepExecutionResult.SENT;

    }

    private NextStepOfferForm offerForm() {
        // Stateless UI helper. Keep the existing one-argument executor constructor.
        return new NextStepOfferForm(context, humanVerificationHandler);
    }

    static Optional<BigDecimal> parseMinimumAllowedPrice(String message) {
        return NextStepOfferForm.parseMinimumAllowedPrice(message);
    }





















    private void submitOffer(
            Page page,
            ListingResponseDto listing,
            NegotiationStepDto nextStep
    ) {

        humanVerificationHandler.waitUntilVerified(
                page
        );

        Locator submitButton =
                page.getByTestId(
                                NegotiationSelectors.OFFER_SUBMIT_BUTTON
                        )
                        .first();

        submitButton.waitFor(
                new Locator.WaitForOptions()
                        .setState(
                                WaitForSelectorState.VISIBLE
                        )
                        .setTimeout(
                                ELEMENT_TIMEOUT_MS
                        )
        );

        if (!submitButton.isEnabled()) {

            throw new IllegalStateException(
                    "Offer submit button is disabled for marketplace listing "
                            + listing.listingId()
            );

        }

        log.warn(
                "[NEXT STEP REAL] Clicking offer-submit-button. "
                        + "This sends a real offer. Listing: {}, "
                        + "step: {}, configured price: {}",
                listing.listingId(),
                nextStep.getStepNumber(),
                nextStep.getOfferPrice()
        );

        submitButton.click(
                new Locator.ClickOptions()
                        .setTimeout(
                                ELEMENT_TIMEOUT_MS
                        )
        );

    }





    private ListingResponseDto markNextStepStarted(
            ListingResponseDto listing,
            NegotiationStepDto nextStep,
            BigDecimal displayedPrice
    ) {

        UpdateListingRequestDto request =
                new UpdateListingRequestDto(
                        "NEGOTIATING",
                        displayedPrice,
                        nextStep.getStepNumber(),
                        true,
                        listing.conversationId(),
                        listing.conversationUrl()
                );

        ListingResponseDto updatedListing =
                listingClient.updateListing(
                        context.getBot().getId(),
                        listing.id(),
                        request
                );

        if (!"NEGOTIATING".equals(
                updatedListing.status()
        )) {

            throw new IllegalStateException(
                    "Backend returned an unexpected status after "
                            + "sending the next negotiation step. Expected "
                            + "NEGOTIATING, actual: "
                            + updatedListing.status()
            );

        }

        if (!Objects.equals(
                nextStep.getStepNumber(),
                updatedListing.currentStep()
        )) {

            throw new IllegalStateException(
                    "Backend returned an unexpected current step. Expected: "
                            + nextStep.getStepNumber()
                            + ", actual: "
                            + updatedListing.currentStep()
            );

        }

        if (!Objects.equals(
                listing.conversationId(),
                updatedListing.conversationId()
        )) {

            throw new IllegalStateException(
                    "Backend returned an unexpected conversation ID. "
                            + "Expected: "
                            + listing.conversationId()
                            + ", actual: "
                            + updatedListing.conversationId()
            );

        }

        return updatedListing;

    }

    private void sendMessageSafely(
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







    private void validateListing(
            ListingResponseDto listing
    ) {

        if (listing.id() == null) {

            throw new IllegalArgumentException(
                    "Backend listing ID cannot be null"
            );

        }

        if (!"NEGOTIATING".equals(
                listing.status()
        )) {

            throw new IllegalArgumentException(
                    "Next negotiation step can only be processed "
                            + "for a NEGOTIATING listing. Backend listing: "
                            + listing.id()
                            + ", status: "
                            + listing.status()
            );

        }

        if (listing.currentStep() == null
                || listing.currentStep() <= 0) {

            throw new IllegalArgumentException(
                    "Negotiating listing "
                            + listing.id()
                            + " has an invalid current step: "
                            + listing.currentStep()
            );

        }

        if (listing.conversationId() == null
                || listing.conversationId().isBlank()) {

            throw new IllegalArgumentException(
                    "Negotiating listing "
                            + listing.id()
                            + " has no conversation ID"
            );

        }

        if (listing.conversationUrl() == null
                || listing.conversationUrl().isBlank()) {

            throw new IllegalArgumentException(
                    "Negotiating listing "
                            + listing.id()
                            + " has no conversation URL"
            );

        }

    }

    private void validateNextStep(
            ListingResponseDto listing,
            NegotiationStepDto nextStep
    ) {

        if (nextStep.getStepNumber() == null) {

            throw new IllegalArgumentException(
                    "Next negotiation step has no step number"
            );

        }

        if (nextStep.getStepNumber()
                <= listing.currentStep()) {

            throw new IllegalArgumentException(
                    "Next negotiation step must be greater than "
                            + "the current step. Current: "
                            + listing.currentStep()
                            + ", next: "
                            + nextStep.getStepNumber()
            );

        }

        if (nextStep.getOfferPrice() == null) {

            throw new IllegalArgumentException(
                    "Negotiation step "
                            + nextStep.getStepNumber()
                            + " has no offer price"
            );

        }

        if (nextStep.getOfferPrice()
                .signum() <= 0) {

            throw new IllegalArgumentException(
                    "Negotiation step "
                            + nextStep.getStepNumber()
                            + " has an invalid offer price: "
                            + nextStep.getOfferPrice()
            );

        }

        if (listing.currentPrice() != null) {
            int priceComparison =
                    nextStep.getOfferPrice().compareTo(listing.currentPrice());

            if (priceComparison < 0) {
                throw new IllegalArgumentException(
                        "Next negotiation offer cannot be lower than the current offer. Current price: "
                                + listing.currentPrice()
                                + ", next price: "
                                + nextStep.getOfferPrice()
                );
            }

            if (priceComparison == 0
                    && !isAllowedAdaptiveCapPlateau(listing, nextStep)) {
                throw new IllegalArgumentException(
                        "Next negotiation offer may equal the current offer only after the adaptive global cap has been reached. Current price: "
                                + listing.currentPrice()
                                + ", next price: "
                                + nextStep.getOfferPrice()
                );
            }
        }

    }

    boolean isAllowedAdaptiveCapPlateau(
            ListingResponseDto listing,
            NegotiationStepDto nextStep
    ) {
        if (listing == null
                || nextStep == null
                || listing.currentPrice() == null
                || nextStep.getOfferPrice() == null
                || context.getBot() == null) {
            return false;
        }

        BotConfigurationDto configuration =
                context.getBot().getConfiguration();

        if (configuration == null
                || !Boolean.TRUE.equals(
                configuration.getAutoRaiseOfferToVintedMinimum()
        )
                || configuration.getMaxAutomaticOffer() == null
                || configuration.getMaxAutomaticOffer().signum() <= 0) {
            return false;
        }

        BigDecimal cap = configuration.getMaxAutomaticOffer();

        return listing.currentPrice().compareTo(cap) == 0
                && nextStep.getOfferPrice().compareTo(cap) == 0
                && listing.currentStep() != null
                && nextStep.getStepNumber() != null
                && nextStep.getStepNumber() > listing.currentStep();
    }



}