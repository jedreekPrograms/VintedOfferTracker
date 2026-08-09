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
import java.math.RoundingMode;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

@Slf4j
@RequiredArgsConstructor
public class NegotiationExecutor {

    private static final double NAVIGATION_TIMEOUT_MS =
            30_000;

    private static final double ELEMENT_TIMEOUT_MS =
            15_000;

    private static final double LISTING_STATE_TIMEOUT_MS =
            15_000;

    private static final double LISTING_STATE_POLL_INTERVAL_MS =
            500;

    private static final double FORM_OPEN_TIMEOUT_MS =
            5_000;

    private static final double OFFER_VALIDATION_TIMEOUT_MS =
            1_500;

    private static final double MODAL_CLOSE_TIMEOUT_MS =
            3_000;

    private static final double CONVERSATION_TIMEOUT_MS =
            30_000;

    private static final double CHAT_ELEMENT_TIMEOUT_MS =
            20_000;

    private static final double MESSAGE_CONFIRMATION_TIMEOUT_MS =
            5_000;

    private static final double MESSAGE_CONFIRMATION_POLL_INTERVAL_MS =
            250;

    private final BotContext context;

    private static final String VINTED_BASE_URL =
            "https://www.vinted.pl";

    private static final BigDecimal VINTED_MIN_OFFER_RATIO =
            new BigDecimal("0.60");


    /*
     * Na razie ListingClient tworzymy tutaj, dzięki czemu obecny konstruktor:
     *
     * new NegotiationExecutor(context)
     *
     * nadal działa i BotWorker nie wymaga jeszcze zmiany.
     */
    private final ListingClient listingClient =
            new ListingClient();

    private final HumanVerificationHandler humanVerificationHandler =
            new HumanVerificationHandler();

    public NegotiationPreparationResult prepareFirstOfferDryRun(
            ListingResponseDto listing
    ) {

        Objects.requireNonNull(
                listing,
                "Listing cannot be null"
        );

        validateListing(
                listing
        );

        NegotiationStepDto firstStep =
                getFirstNegotiationStep();

        BigDecimal offerPrice =
                firstStep.getOfferPrice();

        validateOfferPrice(
                offerPrice
        );

        if (isBelowEstimatedVintedMinimum(
                listing,
                offerPrice,
                "[DRY RUN]"
        )) {

            return NegotiationPreparationResult
                    .OFFER_TOO_LOW;
        }

        log.info(
                "[DRY RUN] Preparing first offer for backend listing {}, "
                        + "marketplace listing {}, price {}",
                listing.id(),
                listing.listingId(),
                offerPrice
        );

        Page page =
                context.getPage();

        navigateToListing(
                page,
                listing,
                "[DRY RUN]"
        );

        boolean listingAvailable =
                waitForListingState(
                        page,
                        listing,
                        "[DRY RUN]"
                );

        if (!listingAvailable) {

            log.info(
                    "[DRY RUN] Backend listing {}, marketplace listing {} "
                            + "is no longer available.",
                    listing.id(),
                    listing.listingId()
            );

            return NegotiationPreparationResult
                    .LISTING_UNAVAILABLE;

        }

        openOfferModal(
                page,
                "[DRY RUN]"
        );

        boolean offerPrepared =
                fillOfferPrice(
                        page,
                        listing,
                        offerPrice,
                        "[DRY RUN]"
                );

        if (!offerPrepared) {

            log.info(
                    "[DRY RUN] Listing {} was skipped because "
                            + "the proposed price was too low. "
                            + "No offer was sent.",
                    listing.listingId()
            );

            return NegotiationPreparationResult
                    .OFFER_TOO_LOW;

        }

        log.info(
                "[DRY RUN] Offer form prepared for listing {} with price {}. "
                        + "Submit button was NOT clicked.",
                listing.listingId(),
                offerPrice
        );

        return NegotiationPreparationResult.PREPARED;

    }

    public NegotiationStartResult startFirstNegotiation(
            ListingResponseDto listing
    ) {

        Objects.requireNonNull(
                listing,
                "Listing cannot be null"
        );

        validateListing(
                listing
        );

        NegotiationStepDto firstStep =
                getFirstNegotiationStep();

        BigDecimal offerPrice =
                firstStep.getOfferPrice();

        Integer stepNumber =
                firstStep.getStepNumber();

        validateOfferPrice(
                offerPrice
        );

        if (isBelowEstimatedVintedMinimum(
                listing,
                offerPrice,
                "[REAL OFFER]"
        )) {

            return NegotiationStartResult
                    .OFFER_TOO_LOW;
        }

        if (stepNumber == null) {

            throw new IllegalStateException(
                    "First negotiation step has no step number"
            );

        }

        log.info(
                "[REAL OFFER] Starting negotiation for backend listing {}, "
                        + "marketplace listing {}, step {}, price {}",
                listing.id(),
                listing.listingId(),
                stepNumber,
                offerPrice
        );

        Page page =
                context.getPage();

        navigateToListing(
                page,
                listing,
                "[REAL OFFER]"
        );

        boolean listingAvailable =
                waitForListingState(
                        page,
                        listing,
                        "[REAL OFFER]"
                );

        if (!listingAvailable) {

            log.info(
                    "[REAL OFFER] Backend listing {}, marketplace listing {} "
                            + "is no longer available. "
                            + "No offer was sent.",
                    listing.id(),
                    listing.listingId()
            );

            return NegotiationStartResult
                    .LISTING_UNAVAILABLE;

        }

        openOfferModal(
                page,
                "[REAL OFFER]"
        );

        boolean offerPrepared =
                fillOfferPrice(
                        page,
                        listing,
                        offerPrice,
                        "[REAL OFFER]"
                );

        if (!offerPrepared) {

            log.info(
                    "[REAL OFFER] Listing {} was skipped because "
                            + "the proposed price was below the minimum "
                            + "allowed by Vinted. No offer was sent.",
                    listing.listingId()
            );

            return NegotiationStartResult
                    .OFFER_TOO_LOW;

        }

        submitFirstOffer(
                page,
                listing
        );

        String conversationUrl =
                waitForConversationUrl(
                        page,
                        listing
                );

        String conversationId =
                extractConversationId(
                        conversationUrl
                );

        validateConversationReferrer(
                conversationUrl,
                listing
        );

        /*
         * Najpierw zapisujemy w backendzie, że oferta została wysłana.
         *
         * Jest to ważniejsze niż wiadomość tekstowa. Gdy wysyłanie wiadomości
         * się nie powiedzie, rekord nie pozostanie jako DISCOVERED i bot
         * nie wyśle drugi raz tej samej oferty.
         */
        ListingResponseDto updatedListing =
                markNegotiationStarted(
                        listing,
                        offerPrice,
                        stepNumber,
                        conversationId,
                        conversationUrl
                );

        log.info(
                "[REAL OFFER] Backend listing {} was updated. "
                        + "Status: {}, conversationId: {}, step: {}, price: {}",
                updatedListing.id(),
                updatedListing.status(),
                updatedListing.conversationId(),
                updatedListing.currentStep(),
                updatedListing.currentPrice()
        );

        verifySubmittedOfferSafely(
                page,
                listing,
                offerPrice
        );

        sendFirstMessageSafely(
                page,
                listing,
                firstStep
        );

        log.info(
                "[REAL OFFER] Negotiation started for marketplace listing {}. "
                        + "Conversation ID: {}",
                listing.listingId(),
                conversationId
        );

        return NegotiationStartResult.STARTED;

    }

    private void navigateToListing(
            Page page,
            ListingResponseDto listing,
            String logPrefix
    ) {

        String listingUrl =
                resolveListingUrl(
                        listing.url()
                );

        log.info(
                "{} Opening listing {}: {}",
                logPrefix,
                listing.listingId(),
                listingUrl
        );

        page.navigate(
                listingUrl,
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

        log.info(
                "{} Listing navigation completed. Current URL: {}",
                logPrefix,
                page.url()
        );

    }

    private boolean waitForListingState(
            Page page,
            ListingResponseDto listing,
            String logPrefix
    ) {

        Locator offerButton =
                page.getByTestId(
                                NegotiationSelectors.ITEM_OFFER_BUTTON
                        )
                        .first();

        long deadline =
                System.currentTimeMillis()
                        + (long) LISTING_STATE_TIMEOUT_MS;

        while (System.currentTimeMillis() < deadline) {

            humanVerificationHandler.waitUntilVerified(
                    page
            );

            if (isListingUnavailable(
                    page
            )) {

                log.info(
                        "{} Vinted displayed Page not found "
                                + "for listing {}",
                        logPrefix,
                        listing.listingId()
                );

                return false;

            }

            if (offerButton.isVisible()) {

                log.info(
                        "{} Listing {} is available. "
                                + "Offer button is visible.",
                        logPrefix,
                        listing.listingId()
                );

                return true;

            }

            page.waitForTimeout(
                    LISTING_STATE_POLL_INTERVAL_MS
            );

        }

        if (isListingUnavailable(
                page
        )) {

            return false;

        }

        throw new IllegalStateException(
                "Listing page did not become available or unavailable "
                        + "within "
                        + Math.round(
                        LISTING_STATE_TIMEOUT_MS / 1_000
                )
                        + " seconds. Marketplace listing: "
                        + listing.listingId()
                        + ", URL: "
                        + page.url()
        );

    }

    private boolean isListingUnavailable(
            Page page
    ) {

        try {

            String title =
                    page.title();

            String bodyText =
                    page.locator(
                                    "body"
                            )
                            .innerText();

            String pageText =
                    (
                            (title == null ? "" : title)
                                    + " "
                                    + (bodyText == null ? "" : bodyText)
                    )
                            .toLowerCase(
                                    Locale.ROOT
                            );

            return pageText.contains(
                    "page not found"
            )
                    || pageText.contains(
                    "check the link is correct"
            )
                    || pageText.contains(
                    "nie znaleziono strony"
            )
                    || pageText.contains(
                    "sprawdź, czy link jest poprawny"
            )
                    || pageText.contains(
                    "item is no longer available"
            )
                    || pageText.contains(
                    "ogłoszenie nie jest już dostępne"
            );

        } catch (PlaywrightException exception) {

            log.debug(
                    "Page was changing while checking listing availability",
                    exception
            );

            return false;

        }

    }

    private void openOfferModal(
            Page page,
            String logPrefix
    ) {

        humanVerificationHandler.waitUntilVerified(
                page
        );

        Locator offerButton =
                page.getByTestId(
                                NegotiationSelectors.ITEM_OFFER_BUTTON
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
                    "Offer form was already visible before bot clicked "
                            + "the offer button"
            );

        }

        log.info(
                "{} Offer button found. Visible: {}, enabled: {}",
                logPrefix,
                offerButton.isVisible(),
                offerButton.isEnabled()
        );

        log.info(
                "{} Bot is performing normal click "
                        + "on item-buyer-offer-button",
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
                priceInput,
                FORM_OPEN_TIMEOUT_MS
        )) {

            log.info(
                    "{} Offer form became visible "
                            + "after normal Playwright click.",
                    logPrefix
            );

            return;

        }

        log.warn(
                "{} Normal Playwright click did not open "
                        + "the offer form. Trying JavaScript click.",
                logPrefix
        );

        humanVerificationHandler.waitUntilVerified(
                page
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
                priceInput,
                FORM_OPEN_TIMEOUT_MS
        )) {

            log.info(
                    "{} Offer form became visible "
                            + "after JavaScript click.",
                    logPrefix
            );

            return;

        }

        throw new IllegalStateException(
                "Bot clicked item-buyer-offer-button, but "
                        + "offer-price-field--input did not appear. "
                        + "Current URL: "
                        + page.url()
        );

    }

    private boolean waitForOfferForm(
            Locator priceInput,
            double timeoutMs
    ) {

        try {

            priceInput.waitFor(
                    new Locator.WaitForOptions()
                            .setState(
                                    WaitForSelectorState.VISIBLE
                            )
                            .setTimeout(
                                    timeoutMs
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
            BigDecimal offerPrice,
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

        String priceText =
                offerPrice.toPlainString();

        priceInput.fill(
                priceText
        );

        String enteredValue =
                priceInput.inputValue();

        if (!priceText.equals(
                enteredValue
        )) {

            throw new IllegalStateException(
                    "Offer input contains unexpected value. Expected: "
                            + priceText
                            + ", actual: "
                            + enteredValue
            );

        }

        log.info(
                "{} Filled offer input. Expected value: {}, "
                        + "actual input value: {}",
                logPrefix,
                priceText,
                enteredValue
        );

        /*
         * Utrata fokusu uruchamia walidację formularza Vinted.
         */
        priceInput.press(
                "Tab"
        );

        if (isOfferTooLow(
                page
        )) {

            log.info(
                    "{} Skipping backend listing {}, "
                            + "marketplace listing {}. Proposed price {} "
                            + "is below the minimum allowed by Vinted.",
                    logPrefix,
                    listing.id(),
                    listing.listingId(),
                    offerPrice
            );

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

        log.info(
                "{} Submit button found and visible.",
                logPrefix
        );

        return true;

    }

    private boolean isOfferTooLow(
            Page page
    ) {

        Locator errorMessage =
                page.getByText(
                                Pattern.compile(
                                        "Wartość jest zbyt niska",
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
                    "{} Offer form did not disappear after "
                            + "pressing Escape. The listing will still "
                            + "be skipped.",
                    logPrefix
            );

        }

    }

    private void submitFirstOffer(
            Page page,
            ListingResponseDto listing
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
                    "Offer submit button is disabled for listing "
                            + listing.listingId()
            );

        }

        log.warn(
                "[REAL OFFER] Clicking offer-submit-button for "
                        + "marketplace listing {}. "
                        + "This action sends a real offer.",
                listing.listingId()
        );

        submitButton.click(
                new Locator.ClickOptions()
                        .setTimeout(
                                ELEMENT_TIMEOUT_MS
                        )
        );

    }

    private String waitForConversationUrl(
            Page page,
            ListingResponseDto listing
    ) {

        try {

            page.waitForURL(
                    "**/inbox/**",
                    new Page.WaitForURLOptions()
                            .setTimeout(
                                    CONVERSATION_TIMEOUT_MS
                            )
            );

        } catch (TimeoutError exception) {

            throw new IllegalStateException(
                    "Offer submit button was clicked, but Vinted did not "
                            + "navigate to an inbox conversation within "
                            + Math.round(
                            CONVERSATION_TIMEOUT_MS / 1_000
                    )
                            + " seconds. Marketplace listing: "
                            + listing.listingId()
                            + ", current URL: "
                            + page.url(),
                    exception
            );

        }

        humanVerificationHandler.waitUntilVerified(
                page
        );

        String conversationUrl =
                page.url();

        if (conversationUrl == null
                || conversationUrl.isBlank()
                || !conversationUrl.contains(
                "/inbox/"
        )) {

            throw new IllegalStateException(
                    "Invalid conversation URL after sending offer: "
                            + conversationUrl
            );

        }

        log.info(
                "[REAL OFFER] Vinted opened conversation for listing {}. "
                        + "URL: {}",
                listing.listingId(),
                conversationUrl
        );

        return conversationUrl;

    }

    private String extractConversationId(
            String conversationUrl
    ) {

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

        for (int i = 0;
             i < pathParts.length - 1;
             i++) {

            if ("inbox".equals(
                    pathParts[i]
            )) {

                String conversationId =
                        pathParts[i + 1];

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

    private void validateConversationReferrer(
            String conversationUrl,
            ListingResponseDto listing
    ) {

        URI uri =
                URI.create(
                        conversationUrl
                );

        String rawQuery =
                uri.getRawQuery();

        if (rawQuery == null
                || rawQuery.isBlank()) {

            log.warn(
                    "[REAL OFFER] Conversation URL has no query parameters. "
                            + "Cannot verify referrer for listing {}.",
                    listing.listingId()
            );

            return;

        }

        String decodedQuery =
                URLDecoder.decode(
                        rawQuery,
                        StandardCharsets.UTF_8
                );

        if (!decodedQuery.contains(
                listing.listingId()
        )) {

            /*
             * Nie przerywamy działania, ponieważ Vinted może zmienić format
             * referrera. Conversation ID nadal pochodzi z przekierowania
             * wykonanego bezpośrednio po wysłaniu oferty.
             */
            log.warn(
                    "[REAL OFFER] Conversation URL referrer does not contain "
                            + "marketplace listing ID {}. Decoded query: {}",
                    listing.listingId(),
                    decodedQuery
            );

            return;

        }

        log.info(
                "[REAL OFFER] Conversation referrer matches "
                        + "marketplace listing {}.",
                listing.listingId()
        );

    }

    private ListingResponseDto markNegotiationStarted(
            ListingResponseDto listing,
            BigDecimal offerPrice,
            Integer stepNumber,
            String conversationId,
            String conversationUrl
    ) {

        UpdateListingRequestDto request =
                new UpdateListingRequestDto(
                        "NEGOTIATING",
                        offerPrice,
                        stepNumber,
                        true,
                        conversationId,
                        conversationUrl
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
                    "Backend returned unexpected listing status after "
                            + "starting negotiation. Expected NEGOTIATING, "
                            + "actual: "
                            + updatedListing.status()
            );

        }

        if (!conversationId.equals(
                updatedListing.conversationId()
        )) {

            throw new IllegalStateException(
                    "Backend returned unexpected conversation ID. "
                            + "Expected: "
                            + conversationId
                            + ", actual: "
                            + updatedListing.conversationId()
            );

        }

        return updatedListing;

    }

    private void verifySubmittedOfferSafely(
            Page page,
            ListingResponseDto listing,
            BigDecimal expectedPrice
    ) {

        try {

            Locator ownOfferPrice =
                    page.getByTestId(
                                    NegotiationSelectors.OWN_OFFER_PRICE
                            )
                            .last();

            ownOfferPrice.waitFor(
                    new Locator.WaitForOptions()
                            .setState(
                                    WaitForSelectorState.VISIBLE
                            )
                            .setTimeout(
                                    CHAT_ELEMENT_TIMEOUT_MS
                            )
            );

            String rawPrice =
                    ownOfferPrice.innerText();

            BigDecimal actualPrice =
                    parsePrice(
                            rawPrice
                    );

            if (expectedPrice.compareTo(
                    actualPrice
            ) != 0) {

                log.error(
                        "[REAL OFFER] Submitted offer price does not match. "
                                + "Backend listing: {}, expected: {}, "
                                + "displayed in chat: {}, raw text: {}",
                        listing.id(),
                        expectedPrice,
                        actualPrice,
                        rawPrice
                );

            } else {

                log.info(
                        "[REAL OFFER] Submitted offer price confirmed "
                                + "in conversation: {}",
                        actualPrice
                );

            }

            logOwnOfferStatus(
                    page
            );

        } catch (Exception exception) {

            /*
             * Oferta została już wysłana, rozmowa została zapisana,
             * a backend ma status NEGOTIATING. Nie możemy z tego powodu
             * cofnąć procesu ani pozwolić na ponowne wysłanie oferty.
             */
            log.warn(
                    "[REAL OFFER] Could not fully verify the submitted offer "
                            + "inside the conversation for listing {}. "
                            + "Backend remains NEGOTIATING.",
                    listing.listingId(),
                    exception
            );

        }

    }

    private void logOwnOfferStatus(
            Page page
    ) {

        Locator ownOfferStatus =
                page.getByTestId(
                                NegotiationSelectors.OWN_OFFER_STATUS
                        )
                        .last();

        try {

            ownOfferStatus.waitFor(
                    new Locator.WaitForOptions()
                            .setState(
                                    WaitForSelectorState.VISIBLE
                            )
                            .setTimeout(
                                    CHAT_ELEMENT_TIMEOUT_MS
                            )
            );

            log.info(
                    "[REAL OFFER] Current own-offer status displayed "
                            + "by Vinted: {}",
                    ownOfferStatus.innerText()
            );

        } catch (TimeoutError exception) {

            log.warn(
                    "[REAL OFFER] Own-offer status did not become visible "
                            + "inside the conversation."
            );

        }

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

        return new BigDecimal(
                normalized
        );

    }

    private void sendFirstMessageSafely(
            Page page,
            ListingResponseDto listing,
            NegotiationStepDto firstStep
    ) {

        String message =
                firstStep.getMessage();

        if (message == null
                || message.isBlank()) {

            log.info(
                    "[REAL OFFER] First negotiation step has no message. "
                            + "Only the price offer was sent for listing {}.",
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
                                    CHAT_ELEMENT_TIMEOUT_MS
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
                        "Chat input contains unexpected message"
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
                                    CHAT_ELEMENT_TIMEOUT_MS
                            )
            );

            log.info(
                    "[REAL OFFER] Sending negotiation message "
                            + "for marketplace listing {}.",
                    listing.listingId()
            );

            sendButton.click(
                    new Locator.ClickOptions()
                            .setTimeout(
                                    CHAT_ELEMENT_TIMEOUT_MS
                            )
            );

            boolean composerCleared =
                    waitForComposerToClear(
                            page,
                            messageInput
                    );

            if (composerCleared) {

                log.info(
                        "[REAL OFFER] Negotiation message was sent "
                                + "for marketplace listing {}.",
                        listing.listingId()
                );

            } else {

                log.warn(
                        "[REAL OFFER] Send button was clicked, but "
                                + "the message input did not clear. "
                                + "The message may require manual verification "
                                + "for marketplace listing {}.",
                        listing.listingId()
                );

            }

        } catch (Exception exception) {

            /*
             * Nie rzucamy wyjątku dalej. Oferta została już wysłana,
             * conversationId zapisano, a listing ma status NEGOTIATING.
             */
            log.error(
                    "[REAL OFFER] Price offer was sent, but the negotiation "
                            + "message could not be sent for listing {}. "
                            + "Backend remains NEGOTIATING.",
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
                 * Przeładowanie elementu po wysłaniu wiadomości także może
                 * oznaczać, że formularz został odświeżony.
                 */
                return true;

            }

            page.waitForTimeout(
                    MESSAGE_CONFIRMATION_POLL_INTERVAL_MS
            );

        }

        return false;

    }

    private NegotiationStepDto getFirstNegotiationStep() {

        if (context.getBot()
                .getConfiguration() == null) {

            throw new IllegalStateException(
                    "Bot configuration is missing"
            );

        }

        List<NegotiationStepDto> negotiationSteps =
                context.getBot()
                        .getConfiguration()
                        .getNegotiationSteps();

        if (negotiationSteps == null
                || negotiationSteps.isEmpty()) {

            throw new IllegalStateException(
                    "Bot has no negotiation steps"
            );

        }

        NegotiationStepDto firstStep =
                negotiationSteps.stream()
                        .filter(
                                Objects::nonNull
                        )
                        .min(
                                Comparator.comparing(
                                        NegotiationStepDto::getStepNumber,
                                        Comparator.nullsLast(
                                                Comparator.naturalOrder()
                                        )
                                )
                        )
                        .orElseThrow(
                                () -> new IllegalStateException(
                                        "Bot has no valid negotiation steps"
                                )
                        );

        if (firstStep.getOfferPrice() == null) {

            throw new IllegalStateException(
                    "First negotiation step has no offer price"
            );

        }

        return firstStep;

    }

    private void validateOfferPrice(
            BigDecimal offerPrice
    ) {

        if (offerPrice.signum() <= 0) {

            throw new IllegalArgumentException(
                    "Offer price must be greater than zero"
            );

        }

    }

    private void validateListing(
            ListingResponseDto listing
    ) {

        if (listing.id() == null) {

            throw new IllegalArgumentException(
                    "Backend listing id cannot be null"
            );

        }

        if (listing.listingId() == null
                || listing.listingId().isBlank()) {

            throw new IllegalArgumentException(
                    "Marketplace listing id cannot be blank"
            );

        }

        if (listing.url() == null
                || listing.url().isBlank()) {

            throw new IllegalArgumentException(
                    "Listing URL cannot be blank"
            );

        }

        if (!"DISCOVERED".equals(
                listing.status()
        )) {

            throw new IllegalArgumentException(
                    "Cannot start negotiation for listing "
                            + listing.id()
                            + " because its status is "
                            + listing.status()
            );

        }

    }

    private String resolveListingUrl(
            String url
    ) {

        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException(
                    "Listing URL cannot be empty"
            );
        }

        if (url.startsWith("https://")
                || url.startsWith("http://")) {

            return url;
        }

        if (url.startsWith("/")) {

            return VINTED_BASE_URL + url;
        }

        return VINTED_BASE_URL + "/" + url;
    }

    private boolean isBelowEstimatedVintedMinimum(
            ListingResponseDto listing,
            BigDecimal offerPrice,
            String logPrefix
    ) {

        BigDecimal listingPrice =
                listing.originalPrice();

        if (listingPrice == null
                || listingPrice.signum() <= 0) {

            return false;
        }

        BigDecimal estimatedMinimum =
                listingPrice
                        .multiply(
                                VINTED_MIN_OFFER_RATIO
                        )
                        .setScale(
                                2,
                                RoundingMode.HALF_UP
                        );

        if (offerPrice.compareTo(
                estimatedMinimum
        ) >= 0) {

            return false;
        }

        log.info(
                "{} Skipping marketplace listing {} before opening it. "
                        + "Listing price: {}, configured offer: {}, "
                        + "estimated Vinted minimum offer: {}.",
                logPrefix,
                listing.listingId(),
                listingPrice,
                offerPrice,
                estimatedMinimum
        );

        return true;
    }

}