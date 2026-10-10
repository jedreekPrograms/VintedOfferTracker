package pl.flipbot.playwright.negotiation;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.listing.ListingClient;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.context.BotContext;
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

        new NextStepPreflightValidator(context).validateListing(
                listing
        );

        new NextStepPreflightValidator(context).validateNextStep(
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

        new NextStepPreflightValidator(context).validateListing(
                listing
        );

        new NextStepPreflightValidator(context).validateNextStep(
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
                new NextStepBackendPersistence(context, listingClient)
                        .markNextStepStarted(
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

        new NextStepMessageDelivery(humanVerificationHandler).sendMessageSafely(
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

    boolean isAllowedAdaptiveCapPlateau(
            ListingResponseDto listing,
            NegotiationStepDto nextStep
    ) {
        // Preserve the package-visible API used by cap-plateau regression tests.
        return new NextStepPreflightValidator(context)
                .isAllowedAdaptiveCapPlateau(listing, nextStep);
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























}