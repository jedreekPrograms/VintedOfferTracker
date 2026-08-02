package pl.flipbot.playwright.negotiation;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.WaitUntilState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.listing.ListingClient;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.api.listing.dto.UpdateListingRequestDto;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.model.NegotiationStepDto;
import pl.flipbot.playwright.verification.HumanVerificationHandler;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Objects;
import java.util.regex.Pattern;

@Slf4j
@RequiredArgsConstructor
public class NextNegotiationStepExecutor {

    private static final double NAVIGATION_TIMEOUT_MS =
            30_000;

    private static final double ELEMENT_TIMEOUT_MS =
            15_000;

    private static final double CONVERSATION_TIMEOUT_MS =
            20_000;

    private static final double FORM_OPEN_TIMEOUT_MS =
            5_000;

    private static final double OFFER_VALIDATION_TIMEOUT_MS =
            1_500;

    private static final double MODAL_CLOSE_TIMEOUT_MS =
            3_000;

    private static final double OFFER_CONFIRMATION_TIMEOUT_MS =
            30_000;

    private static final double OFFER_CONFIRMATION_POLL_INTERVAL_MS =
            500;

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

        openConversation(
                page,
                listing,
                "[NEXT STEP DRY RUN]"
        );

        openOfferModal(
                page,
                listing,
                "[NEXT STEP DRY RUN]"
        );

        boolean offerPrepared =
                fillOfferPrice(
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

        openConversation(
                page,
                listing,
                "[NEXT STEP REAL]"
        );

        openOfferModal(
                page,
                listing,
                "[NEXT STEP REAL]"
        );

        boolean offerPrepared =
                fillOfferPrice(
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

        SubmittedOffer submittedOffer =
                waitForNewOwnOffer(
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

    private void openConversation(
            Page page,
            ListingResponseDto listing,
            String logPrefix
    ) {

        log.info(
                "{} Opening conversation {}: {}",
                logPrefix,
                listing.conversationId(),
                listing.conversationUrl()
        );

        page.navigate(
                listing.conversationUrl(),
                new Page.NavigateOptions()
                        .setWaitUntil(
                                WaitUntilState.DOMCONTENTLOADED
                        )
                        .setTimeout(
                                NAVIGATION_TIMEOUT_MS
                        )
        );

        humanVerificationHandler.waitUntilVerified(
                page
        );

        validateOpenedConversation(
                page,
                listing
        );

        Locator conversationContent =
                page.getByTestId(
                                "conversation-content"
                        )
                        .first();

        conversationContent.waitFor(
                new Locator.WaitForOptions()
                        .setState(
                                WaitForSelectorState.VISIBLE
                        )
                        .setTimeout(
                                CONVERSATION_TIMEOUT_MS
                        )
        );

        log.info(
                "{} Conversation {} is ready.",
                logPrefix,
                listing.conversationId()
        );

    }

    private void openOfferModal(
            Page page,
            ListingResponseDto listing,
            String logPrefix
    ) {

        humanVerificationHandler.waitUntilVerified(
                page
        );

        Locator offerButton =
                page.getByTestId(
                                NegotiationSelectors.CHAT_OFFER_BUTTON
                        )
                        .first();

        Locator priceInput =
                page.getByTestId(
                                NegotiationSelectors.OFFER_PRICE_INPUT
                        )
                        .first();

        offerButton.waitFor(
                new Locator.WaitForOptions()
                        .setState(
                                WaitForSelectorState.VISIBLE
                        )
                        .setTimeout(
                                ELEMENT_TIMEOUT_MS
                        )
        );

        offerButton.scrollIntoViewIfNeeded();

        if (priceInput.isVisible()) {

            throw new IllegalStateException(
                    "Offer form was already visible before clicking "
                            + "the chat offer button. Marketplace listing: "
                            + listing.listingId()
            );

        }

        log.info(
                "{} Chat offer button found. Visible: {}, enabled: {}",
                logPrefix,
                offerButton.isVisible(),
                offerButton.isEnabled()
        );

        log.info(
                "{} Performing normal click "
                        + "on make-offer-request-button.",
                logPrefix
        );

        offerButton.click(
                new Locator.ClickOptions()
                        .setTimeout(
                                ELEMENT_TIMEOUT_MS
                        )
        );

        humanVerificationHandler.waitUntilVerified(
                page
        );

        if (waitForOfferForm(
                priceInput
        )) {

            log.info(
                    "{} Offer form became visible "
                            + "after normal Playwright click.",
                    logPrefix
            );

            return;

        }

        log.warn(
                "{} Normal click did not open the offer form. "
                        + "Trying JavaScript click.",
                logPrefix
        );

        offerButton.waitFor(
                new Locator.WaitForOptions()
                        .setState(
                                WaitForSelectorState.VISIBLE
                        )
                        .setTimeout(
                                ELEMENT_TIMEOUT_MS
                        )
        );

        offerButton.evaluate(
                "element => element.click()"
        );

        humanVerificationHandler.waitUntilVerified(
                page
        );

        if (waitForOfferForm(
                priceInput
        )) {

            log.info(
                    "{} Offer form became visible "
                            + "after JavaScript click.",
                    logPrefix
            );

            return;

        }

        throw new IllegalStateException(
                "Bot clicked make-offer-request-button, but "
                        + "offer-price-field--input did not appear. "
                        + "Marketplace listing: "
                        + listing.listingId()
                        + ", conversation: "
                        + listing.conversationId()
                        + ", current URL: "
                        + page.url()
        );

    }

    private boolean waitForOfferForm(
            Locator priceInput
    ) {

        try {

            priceInput.waitFor(
                    new Locator.WaitForOptions()
                            .setState(
                                    WaitForSelectorState.VISIBLE
                            )
                            .setTimeout(
                                    FORM_OPEN_TIMEOUT_MS
                            )
            );

            return true;

        } catch (TimeoutError exception) {

            return false;

        }

    }

    private boolean fillOfferPrice(
            Page page,
            ListingResponseDto listing,
            NegotiationStepDto nextStep,
            String logPrefix
    ) {

        humanVerificationHandler.waitUntilVerified(
                page
        );

        Locator priceInput =
                page.getByTestId(
                                NegotiationSelectors.OFFER_PRICE_INPUT
                        )
                        .first();

        priceInput.waitFor(
                new Locator.WaitForOptions()
                        .setState(
                                WaitForSelectorState.VISIBLE
                        )
                        .setTimeout(
                                ELEMENT_TIMEOUT_MS
                        )
        );

        String expectedPrice =
                nextStep.getOfferPrice()
                        .toPlainString();

        priceInput.fill(
                expectedPrice
        );

        String actualInputValue =
                priceInput.inputValue();

        if (!expectedPrice.equals(
                actualInputValue
        )) {

            throw new IllegalStateException(
                    "Offer input contains an unexpected value. Expected: "
                            + expectedPrice
                            + ", actual: "
                            + actualInputValue
            );

        }

        log.info(
                "{} Filled offer input for listing {}. "
                        + "Expected: {}, actual: {}",
                logPrefix,
                listing.listingId(),
                expectedPrice,
                actualInputValue
        );

        priceInput.press(
                "Tab"
        );

        if (isOfferTooLow(
                page,
                logPrefix
        )) {

            closeOfferModal(
                    page,
                    logPrefix
            );

            return false;

        }

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
                    "Offer submit button is disabled after entering price "
                            + expectedPrice
                            + " for marketplace listing "
                            + listing.listingId()
            );

        }

        log.info(
                "{} Submit button is visible and enabled.",
                logPrefix
        );

        return true;

    }

    private boolean isOfferTooLow(
            Page page,
            String logPrefix
    ) {

        Locator errorMessage =
                page.getByText(
                                Pattern.compile(
                                        "Wartość jest zbyt niska"
                                                + "|Minimalna wartość nie może "
                                                + "być niższa",
                                        Pattern.CASE_INSENSITIVE
                                )
                        )
                        .first();

        try {

            errorMessage.waitFor(
                    new Locator.WaitForOptions()
                            .setState(
                                    WaitForSelectorState.VISIBLE
                            )
                            .setTimeout(
                                    OFFER_VALIDATION_TIMEOUT_MS
                            )
            );

            log.warn(
                    "{} Vinted rejected the configured price as too low. "
                            + "Validation message: {}",
                    logPrefix,
                    errorMessage.innerText()
            );

            return true;

        } catch (TimeoutError exception) {

            return false;

        }

    }

    private void closeOfferModal(
            Page page,
            String logPrefix
    ) {

        Locator priceInput =
                page.getByTestId(
                                NegotiationSelectors.OFFER_PRICE_INPUT
                        )
                        .first();

        log.info(
                "{} Closing offer form.",
                logPrefix
        );

        page.keyboard().press(
                "Escape"
        );

        try {

            priceInput.waitFor(
                    new Locator.WaitForOptions()
                            .setState(
                                    WaitForSelectorState.HIDDEN
                            )
                            .setTimeout(
                                    MODAL_CLOSE_TIMEOUT_MS
                            )
            );

            log.info(
                    "{} Offer form was closed.",
                    logPrefix
            );

        } catch (TimeoutError exception) {

            log.warn(
                    "{} Offer form did not disappear after pressing Escape. "
                            + "No offer was sent.",
                    logPrefix
            );

        }

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

    private SubmittedOffer waitForNewOwnOffer(
            Page page,
            ListingResponseDto listing,
            NegotiationStepDto nextStep,
            int ownOfferCountBefore
    ) {

        Locator ownOfferPrices =
                page.getByTestId(
                        NegotiationSelectors.OWN_OFFER_PRICE
                );

        long deadline =
                System.currentTimeMillis()
                        + (long) OFFER_CONFIRMATION_TIMEOUT_MS;

        while (System.currentTimeMillis() < deadline) {

            humanVerificationHandler.waitUntilVerified(
                    page
            );

            int currentOfferCount =
                    ownOfferPrices.count();

            if (currentOfferCount
                    > ownOfferCountBefore) {

                Locator latestOwnOfferPrice =
                        ownOfferPrices.nth(
                                currentOfferCount - 1
                        );

                if (!latestOwnOfferPrice.isVisible()) {

                    page.waitForTimeout(
                            OFFER_CONFIRMATION_POLL_INTERVAL_MS
                    );

                    continue;

                }

                String rawPrice =
                        latestOwnOfferPrice.innerText();

                BigDecimal displayedPrice =
                        parsePrice(
                                rawPrice
                        );

                String rawStatus =
                        readLatestOwnOfferStatus(
                                page
                        );

                if (displayedPrice.compareTo(
                        nextStep.getOfferPrice()
                ) != 0) {

                    /*
                     * Przy ofertach między różnymi walutami Vinted może
                     * nieznacznie zmienić wyświetloną cenę.
                     *
                     * Do backendu zapisujemy cenę faktycznie pokazaną
                     * w rozmowie.
                     */
                    log.warn(
                            "[NEXT STEP REAL] Configured offer price {} "
                                    + "differs from the price displayed "
                                    + "by Vinted: {}. Raw price: {}",
                            nextStep.getOfferPrice(),
                            displayedPrice,
                            rawPrice
                    );

                } else {

                    log.info(
                            "[NEXT STEP REAL] Submitted offer price "
                                    + "was confirmed in conversation: {}",
                            displayedPrice
                    );

                }

                log.info(
                        "[NEXT STEP REAL] New own offer appeared in "
                                + "conversation. Previous offer count: {}, "
                                + "current offer count: {}, status: {}",
                        ownOfferCountBefore,
                        currentOfferCount,
                        rawStatus
                );

                return new SubmittedOffer(
                        displayedPrice,
                        rawStatus
                );

            }

            page.waitForTimeout(
                    OFFER_CONFIRMATION_POLL_INTERVAL_MS
            );

        }

        /*
         * Po kliknięciu przycisku oferta mogła zostać wysłana, mimo że
         * DOM nie został poprawnie odczytany. Nie można wtedy bezmyślnie
         * uruchamiać bota ponownie.
         */
        throw new IllegalStateException(
                "The real next-step submit button was clicked, but "
                        + "a new own offer was not confirmed within "
                        + Math.round(
                        OFFER_CONFIRMATION_TIMEOUT_MS / 1_000
                )
                        + " seconds. The offer may already have been sent. "
                        + "Do not retry automatically. Marketplace listing: "
                        + listing.listingId()
                        + ", conversation: "
                        + listing.conversationId()
        );

    }

    private String readLatestOwnOfferStatus(
            Page page
    ) {

        try {

            Locator ownOfferStatuses =
                    page.getByTestId(
                            NegotiationSelectors.OWN_OFFER_STATUS
                    );

            int statusCount =
                    ownOfferStatuses.count();

            if (statusCount == 0) {
                return null;
            }

            Locator latestStatus =
                    ownOfferStatuses.nth(
                            statusCount - 1
                    );

            if (!latestStatus.isVisible()) {
                return null;
            }

            return latestStatus.innerText();

        } catch (PlaywrightException exception) {

            log.debug(
                    "Conversation DOM changed while reading "
                            + "the latest own-offer status",
                    exception
            );

            return null;

        }

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

            Locator messageInput =
                    page.getByTestId(
                                    NegotiationSelectors.MESSAGE_INPUT
                            )
                            .first();

            messageInput.waitFor(
                    new Locator.WaitForOptions()
                            .setState(
                                    WaitForSelectorState.VISIBLE
                            )
                            .setTimeout(
                                    MESSAGE_TIMEOUT_MS
                            )
            );

            messageInput.fill(
                    message
            );

            String enteredMessage =
                    messageInput.inputValue();

            if (!message.equals(
                    enteredMessage
            )) {

                throw new IllegalStateException(
                        "Chat input contains an unexpected message"
                );

            }

            Locator sendIcon =
                    page.getByTestId(
                                    NegotiationSelectors.MESSAGE_SEND_ICON
                            )
                            .last();

            Locator sendButton =
                    sendIcon.locator(
                                    "xpath=ancestor::button[1]"
                            )
                            .first();

            sendButton.waitFor(
                    new Locator.WaitForOptions()
                            .setState(
                                    WaitForSelectorState.VISIBLE
                            )
                            .setTimeout(
                                    MESSAGE_TIMEOUT_MS
                            )
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
                    waitForComposerToClear(
                            page,
                            messageInput
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

    private boolean waitForComposerToClear(
            Page page,
            Locator messageInput
    ) {

        long deadline =
                System.currentTimeMillis()
                        + (long) MESSAGE_CONFIRMATION_TIMEOUT_MS;

        while (System.currentTimeMillis() < deadline) {

            try {

                if (messageInput.inputValue()
                        .isBlank()) {

                    return true;

                }

            } catch (PlaywrightException exception) {

                /*
                 * Vinted może zastąpić element textarea po wysłaniu.
                 * W takim przypadku traktujemy formularz jako odświeżony.
                 */
                return true;

            }

            page.waitForTimeout(
                    MESSAGE_CONFIRMATION_POLL_INTERVAL_MS
            );

        }

        return false;

    }

    private BigDecimal parsePrice(
            String rawPrice
    ) {

        if (rawPrice == null
                || rawPrice.isBlank()) {

            throw new IllegalArgumentException(
                    "Price text cannot be blank"
            );

        }

        String normalized =
                rawPrice
                        .replace(
                                "\u00A0",
                                ""
                        )
                        .replace(
                                "\u202F",
                                ""
                        )
                        .replace(
                                " ",
                                ""
                        )
                        .replaceAll(
                                "[^0-9,.-]",
                                ""
                        );

        if (normalized.isBlank()) {

            throw new IllegalArgumentException(
                    "Price contains no numeric value: "
                            + rawPrice
            );

        }

        if (normalized.contains(
                ","
        ) && normalized.contains(
                "."
        )) {

            int lastComma =
                    normalized.lastIndexOf(
                            ','
                    );

            int lastDot =
                    normalized.lastIndexOf(
                            '.'
                    );

            if (lastComma > lastDot) {

                normalized =
                        normalized
                                .replace(
                                        ".",
                                        ""
                                )
                                .replace(
                                        ',',
                                        '.'
                                );

            } else {

                normalized =
                        normalized.replace(
                                ",",
                                ""
                        );

            }

        } else if (normalized.contains(
                ","
        )) {

            normalized =
                    normalized.replace(
                            ',',
                            '.'
                    );

        }

        BigDecimal price =
                new BigDecimal(
                        normalized
                );

        if (price.signum() <= 0) {

            throw new IllegalArgumentException(
                    "Price must be greater than zero: "
                            + rawPrice
            );

        }

        return price;

    }

    private void validateOpenedConversation(
            Page page,
            ListingResponseDto listing
    ) {

        String currentUrl =
                page.url();

        String openedConversationId =
                extractConversationId(
                        currentUrl
                );

        if (!listing.conversationId().equals(
                openedConversationId
        )) {

            throw new IllegalStateException(
                    "Opened an unexpected conversation. Expected: "
                            + listing.conversationId()
                            + ", actual: "
                            + openedConversationId
                            + ", URL: "
                            + currentUrl
            );

        }

        log.info(
                "Opened expected conversation {}.",
                openedConversationId
        );

    }

    private String extractConversationId(
            String conversationUrl
    ) {

        if (conversationUrl == null
                || conversationUrl.isBlank()) {

            throw new IllegalArgumentException(
                    "Conversation URL cannot be blank"
            );

        }

        URI uri =
                URI.create(
                        conversationUrl
                );

        String path =
                uri.getPath();

        if (path == null
                || path.isBlank()) {

            throw new IllegalArgumentException(
                    "Conversation URL has no path: "
                            + conversationUrl
            );

        }

        String[] pathParts =
                path.split(
                        "/"
                );

        for (int index = 0;
             index < pathParts.length - 1;
             index++) {

            if ("inbox".equals(
                    pathParts[index]
            )) {

                String conversationId =
                        pathParts[index + 1];

                if (conversationId != null
                        && !conversationId.isBlank()) {

                    return conversationId;

                }

            }

        }

        throw new IllegalArgumentException(
                "Cannot extract conversation ID from URL: "
                        + conversationUrl
        );

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

    }

    private record SubmittedOffer(

            BigDecimal displayedPrice,

            String rawStatus

    ) {
    }

}