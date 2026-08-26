package pl.flipbot.playwright.negotiation;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.WaitUntilState;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.listing.ListingClient;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.api.listing.dto.UpdateListingRequestDto;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.model.NegotiationStepDto;
import pl.flipbot.playwright.verification.HumanVerificationHandler;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

@Slf4j
public class FirstOfferExecutor {

    private static final String VINTED_BASE_URL =
            "https://www.vinted.pl";

    private static final String ITEM_TITLE_SELECTOR =
            "[data-testid='item-page-summary-plugin'] h1";

    private static final Pattern OFFER_BUTTON_NAME =
            Pattern.compile(
                    "^(Zaproponuj cenę|Make an offer)$",
                    Pattern.CASE_INSENSITIVE
            );

    private static final BigDecimal VINTED_MIN_OFFER_RATIO =
            new BigDecimal("0.60");

    private static final double NAVIGATION_TIMEOUT_MS =
            30_000;

    private static final double LISTING_STATE_TIMEOUT_MS =
            15_000;

    private static final double LISTING_STATE_POLL_INTERVAL_MS =
            250;

    private static final double OFFER_BUTTON_TIMEOUT_MS =
            15_000;

    private static final double FORM_OPEN_TIMEOUT_MS =
            5_000;

    private static final double ELEMENT_TIMEOUT_MS =
            15_000;

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

    private final ListingClient listingClient;

    private final HumanVerificationHandler humanVerificationHandler =
            new HumanVerificationHandler();

    private PreparedOffer preparedOffer;


    public FirstOfferExecutor(
            BotContext context
    ) {

        this.context =
                context;

        this.listingClient =
                new ListingClient();
    }


    /**
     * Przygotowuje formularz pierwszej oferty, ale NIE klika submit.
     *
     * Ta metoda musi być wykonana PRZED reserveSlot().
     */
    public NegotiationPreparationResult prepareFirstOffer(
            ListingResponseDto listing
    ) {

        Objects.requireNonNull(
                listing,
                "Listing cannot be null"
        );

        clearPreparedState();

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

        if (
                stepNumber == null
        ) {

            throw new IllegalStateException(
                    "First negotiation step has no step number"
            );
        }

        if (
                isBelowEstimatedVintedMinimum(
                        listing,
                        offerPrice
                )
        ) {

            return NegotiationPreparationResult
                    .OFFER_TOO_LOW;
        }

        Page page =
                context.getPage();

        try {

            log.info(
                    "[REAL OFFER PREPARE] Preparing first offer for "
                            + "backend listing {}, marketplace listing {}, "
                            + "step {}, price {}. No quota has been reserved.",
                    listing.id(),
                    listing.listingId(),
                    stepNumber,
                    offerPrice
            );

            navigateToListingIfNeeded(
                    page,
                    listing
            );

            boolean listingAvailable =
                    waitForListingPage(
                            page,
                            listing
                    );

            if (
                    !listingAvailable
            ) {

                log.info(
                        "[REAL OFFER PREPARE] Marketplace listing {} "
                                + "is no longer available. "
                                + "No quota was reserved and no offer was sent.",
                        listing.listingId()
                );

                return NegotiationPreparationResult
                        .LISTING_UNAVAILABLE;
            }

            Locator offerButton =
                    waitForOfferButtonOrNull(
                            page,
                            listing
                    );

            if (
                    offerButton == null
            ) {

                if (
                        isListingUnavailable(
                                page
                        )
                ) {

                    log.info(
                            "[REAL OFFER PREPARE] Marketplace listing {} "
                                    + "became unavailable while checking "
                                    + "whether negotiation is possible. "
                                    + "No quota was reserved and no offer "
                                    + "was sent.",
                            listing.listingId()
                    );

                    return NegotiationPreparationResult
                            .LISTING_UNAVAILABLE;
                }

                log.warn(
                        "[REAL OFFER PREPARE] Marketplace listing {} is "
                                + "available, but this account currently has "
                                + "no visible action for starting a price "
                                + "negotiation. This can happen because of "
                                + "seller/account restrictions, blocking, or "
                                + "listing-specific Vinted rules. The "
                                + "candidate will be skipped and persisted "
                                + "as CANNOT_NEGOTIATE by the caller. "
                                + "No quota was reserved and no offer was sent.",
                        listing.listingId()
                );

                return NegotiationPreparationResult
                        .CANNOT_NEGOTIATE;
            }

            openOfferModal(
                    page,
                    listing,
                    offerButton
            );

            boolean offerPrepared =
                    fillOfferPrice(
                            page,
                            listing,
                            offerPrice
                    );

            if (
                    !offerPrepared
            ) {

                log.info(
                        "[REAL OFFER PREPARE] Marketplace listing {} "
                                + "was rejected before submission because "
                                + "the proposed price is below the minimum "
                                + "accepted by Vinted. No quota was reserved.",
                        listing.listingId()
                );

                return NegotiationPreparationResult
                        .OFFER_TOO_LOW;
            }

            preparedOffer =
                    new PreparedOffer(
                            listing.id(),
                            listing.listingId(),
                            offerPrice,
                            stepNumber,
                            firstStep.getMessage()
                    );

            assertPreparedOfferReady(
                    listing
            );

            log.warn(
                    "[REAL OFFER PREPARE] Marketplace listing {} is fully "
                            + "prepared for submission. Offer form is open, "
                            + "price={}, submit button is visible and enabled. "
                            + "No quota has been reserved and submit has NOT "
                            + "been clicked.",
                    listing.listingId(),
                    offerPrice
            );

            return NegotiationPreparationResult
                    .PREPARED;

        } catch (RuntimeException exception) {

            clearPreparedState();

            throw exception;
        }
    }


    /**
     * Ostatni check wykonywany jeszcze PRZED reserveSlot().
     */
    public void assertPreparedOfferReady(
            ListingResponseDto listing
    ) {

        requireMatchingPreparedOffer(
                listing
        );

        Page page =
                context.getPage();

        if (
                !isCurrentListingPage(
                        page,
                        listing.listingId()
                )
        ) {

            throw new IllegalStateException(
                    "Prepared offer page changed before quota reservation. "
                            + "Marketplace listing: "
                            + listing.listingId()
                            + ", current URL: "
                            + page.url()
            );
        }

        Locator priceInput =
                page.getByTestId(
                                NegotiationSelectors.OFFER_PRICE_INPUT
                        )
                        .first();

        if (
                !priceInput.isVisible()
        ) {

            throw new IllegalStateException(
                    "Offer form disappeared before quota reservation for "
                            + "marketplace listing "
                            + listing.listingId()
            );
        }

        String expectedPrice =
                preparedOffer.offerPrice()
                        .toPlainString();

        String actualPrice =
                priceInput.inputValue();

        if (
                !expectedPrice.equals(
                        actualPrice
                )
        ) {

            throw new IllegalStateException(
                    "Prepared offer price changed before quota reservation. "
                            + "Expected: "
                            + expectedPrice
                            + ", actual: "
                            + actualPrice
            );
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

        if (
                !submitButton.isEnabled()
        ) {

            throw new IllegalStateException(
                    "Offer submit button is disabled before quota reservation "
                            + "for marketplace listing "
                            + listing.listingId()
            );
        }

        log.info(
                "[REAL OFFER PREPARE] Final pre-quota check passed for "
                        + "marketplace listing {}.",
                listing.listingId()
        );
    }


    /**
     * Wywołuj DOPIERO po udanym reserveSlot().
     *
     * Od momentu rozpoczęcia tej metody błąd traktujemy konserwatywnie:
     * quota nie powinna być automatycznie zwalniana, bo klik submit może
     * zostać wykonany lub jego wynik może być niejednoznaczny.
     */
    public NegotiationStartResult submitPreparedFirstNegotiation(
            ListingResponseDto listing
    ) {

        requireMatchingPreparedOffer(
                listing
        );

        Page page =
                context.getPage();

        PreparedOffer offer =
                preparedOffer;

        Locator submitButton =
                page.getByTestId(
                                NegotiationSelectors.OFFER_SUBMIT_BUTTON
                        )
                        .first();

        log.warn(
                "[REAL OFFER] Quota is reserved. Clicking the offer submit "
                        + "button for marketplace listing {}. "
                        + "This action sends a REAL offer of {}.",
                listing.listingId(),
                offer.offerPrice()
        );

        /*
         * Nie robimy tutaj dodatkowych operacji, które mogłyby rzucić
         * wyjątek przed kliknięciem. Wszystkie bezpieczne checki odbyły się
         * w assertPreparedOfferReady() przed reserveSlot().
         *
         * Jeśli samo click() rzuci wyjątek, wynik dostarczenia może być
         * niejednoznaczny, dlatego caller zachowuje quota.
         */
        submitButton.click(
                new Locator.ClickOptions()
                        .setTimeout(
                                ELEMENT_TIMEOUT_MS
                        )
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

        SubmittedOfferConfirmationVerifier.requireExactOwnOffer(
                page,
                listing.listingId(),
                offer.offerPrice()
        );

        ListingResponseDto updatedListing =
                markNegotiationStarted(
                        listing,
                        offer.offerPrice(),
                        offer.stepNumber(),
                        conversationId,
                        conversationUrl
                );

        log.info(
                "[REAL OFFER] Backend listing {} was updated. "
                        + "Status={}, conversationId={}, step={}, price={}.",
                updatedListing.id(),
                updatedListing.status(),
                updatedListing.conversationId(),
                updatedListing.currentStep(),
                updatedListing.currentPrice()
        );

        logOwnOfferStatus(
                page
        );

        sendFirstMessageSafely(
                page,
                listing,
                offer.message()
        );

        log.warn(
                "[REAL OFFER] Negotiation STARTED for marketplace listing {}. "
                        + "Conversation ID: {}.",
                listing.listingId(),
                conversationId
        );

        clearPreparedState();

        return NegotiationStartResult
                .STARTED;
    }


    public void cancelPreparedOfferSafely() {

        if (
                preparedOffer == null
        ) {

            return;
        }

        try {

            closeOfferModal(
                    context.getPage()
            );

        } catch (Exception exception) {

            log.warn(
                    "[REAL OFFER PREPARE] Could not close prepared offer form "
                            + "for marketplace listing {}: {}",
                    preparedOffer.marketplaceListingId(),
                    getFriendlyErrorMessage(
                            exception
                    )
            );

            log.trace(
                    "[REAL OFFER PREPARE] Full prepared-form close error.",
                    exception
            );

        } finally {

            clearPreparedState();
        }
    }


    private void navigateToListingIfNeeded(
            Page page,
            ListingResponseDto listing
    ) {

        String listingUrl =
                resolveListingUrl(
                        listing.url()
                );

        if (
                isCurrentListingPage(
                        page,
                        listing.listingId()
                )
        ) {

            log.info(
                    "[REAL OFFER PREPARE] Marketplace listing {} is already "
                            + "open after FINAL VERIFY. Reusing current page "
                            + "instead of navigating to the same item again. "
                            + "Current URL: {}",
                    listing.listingId(),
                    page.url()
            );

            humanVerificationHandler.waitUntilVerified(
                    page
            );

            return;
        }

        log.info(
                "[REAL OFFER PREPARE] Opening marketplace listing {}: {}",
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
                "[REAL OFFER PREPARE] Listing navigation completed. "
                        + "Current URL: {}",
                page.url()
        );
    }


    private boolean waitForListingPage(
            Page page,
            ListingResponseDto listing
    ) {

        Locator itemTitle =
                page.locator(
                                ITEM_TITLE_SELECTOR
                        )
                        .first();

        long deadline =
                System.currentTimeMillis()
                        + (long) LISTING_STATE_TIMEOUT_MS;

        while (
                System.currentTimeMillis()
                        < deadline
        ) {

            humanVerificationHandler.waitUntilVerified(
                    page
            );

            if (
                    isListingUnavailable(
                            page
                    )
            ) {

                return false;
            }

            if (
                    itemTitle.isVisible()
            ) {

                String title =
                        normalizeVisibleText(
                                itemTitle.innerText()
                        );

                log.info(
                        "[REAL OFFER PREPARE] Listing page is loaded for {}. "
                                + "Visible h1='{}'.",
                        listing.listingId(),
                        title
                );

                return true;
            }

            page.waitForTimeout(
                    LISTING_STATE_POLL_INTERVAL_MS
            );
        }

        if (
                isListingUnavailable(
                        page
                )
        ) {

            return false;
        }

        throw new IllegalStateException(
                "Listing item page did not expose its h1 within "
                        + Math.round(
                        LISTING_STATE_TIMEOUT_MS / 1_000
                )
                        + " seconds. Marketplace listing: "
                        + listing.listingId()
                        + ", URL: "
                        + page.url()
        );
    }


    private Locator waitForOfferButtonOrNull(
            Page page,
            ListingResponseDto listing
    ) {

        Locator testIdButton =
                page.getByTestId(
                                NegotiationSelectors.ITEM_OFFER_BUTTON
                        )
                        .first();

        Locator accessibleButtons =
                page.getByRole(
                        AriaRole.BUTTON,
                        new Page.GetByRoleOptions()
                                .setName(
                                        OFFER_BUTTON_NAME
                                )
                );

        long deadline =
                System.currentTimeMillis()
                        + (long) OFFER_BUTTON_TIMEOUT_MS;

        while (
                System.currentTimeMillis()
                        < deadline
        ) {

            humanVerificationHandler.waitUntilVerified(
                    page
            );

            if (
                    isListingUnavailable(
                            page
                    )
            ) {

                return null;
            }

            if (
                    testIdButton.isVisible()
            ) {

                log.info(
                        "[REAL OFFER PREPARE] Offer button found by test-id '{}'.",
                        NegotiationSelectors.ITEM_OFFER_BUTTON
                );

                return testIdButton;
            }

            int accessibleCount =
                    accessibleButtons.count();

            for (
                    int index = 0;
                    index < accessibleCount;
                    index++
            ) {

                Locator candidate =
                        accessibleButtons.nth(
                                index
                        );

                if (
                        !candidate.isVisible()
                ) {

                    continue;
                }

                log.warn(
                        "[REAL OFFER PREPARE] Offer button test-id '{}' was not "
                                + "available, but a visible button was found by "
                                + "accessible name '{}'. Using accessible-name "
                                + "fallback.",
                        NegotiationSelectors.ITEM_OFFER_BUTTON,
                        candidate.innerText()
                );

                return candidate;
            }

            page.waitForTimeout(
                    LISTING_STATE_POLL_INTERVAL_MS
            );
        }

        log.info(
                "[REAL OFFER PREPARE] No visible offer action was found "
                        + "within {} seconds for marketplace listing {}. "
                        + "The listing page itself is loaded. Treating this "
                        + "as CANNOT_NEGOTIATE rather than a worker failure.",
                Math.round(
                        OFFER_BUTTON_TIMEOUT_MS / 1_000
                ),
                listing.listingId()
        );

        return null;
    }


    private void openOfferModal(
            Page page,
            ListingResponseDto listing,
            Locator offerButton
    ) {

        Locator priceInput =
                page.getByTestId(
                                NegotiationSelectors.OFFER_PRICE_INPUT
                        )
                        .first();

        if (
                priceInput.isVisible()
        ) {

            throw new IllegalStateException(
                    "Offer form was already visible before opening it for "
                            + "marketplace listing "
                            + listing.listingId()
            );
        }

        offerButton.scrollIntoViewIfNeeded();

        if (
                !offerButton.isEnabled()
        ) {

            throw new IllegalStateException(
                    "Offer button is visible but disabled for marketplace "
                            + "listing "
                            + listing.listingId()
            );
        }

        log.info(
                "[REAL OFFER PREPARE] Clicking offer button for marketplace "
                        + "listing {}. This only opens the offer form; it does "
                        + "NOT send an offer.",
                listing.listingId()
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

        if (
                waitForOfferForm(
                        priceInput,
                        FORM_OPEN_TIMEOUT_MS
                )
        ) {

            return;
        }

        log.warn(
                "[REAL OFFER PREPARE] Normal click did not open the offer "
                        + "form. Trying a JavaScript click."
        );

        offerButton.evaluate(
                "element => element.click()"
        );

        humanVerificationHandler.waitUntilVerified(
                page
        );

        if (
                waitForOfferForm(
                        priceInput,
                        FORM_OPEN_TIMEOUT_MS
                )
        ) {

            return;
        }

        throw new IllegalStateException(
                "Offer button was clicked, but offer-price-field--input did "
                        + "not appear. Marketplace listing: "
                        + listing.listingId()
                        + ", current URL: "
                        + page.url()
        );
    }


    private boolean fillOfferPrice(
            Page page,
            ListingResponseDto listing,
            BigDecimal offerPrice
    ) {

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

        if (
                !priceText.equals(
                        enteredValue
                )
        ) {

            throw new IllegalStateException(
                    "Offer input contains unexpected value. Expected: "
                            + priceText
                            + ", actual: "
                            + enteredValue
            );
        }

        log.info(
                "[REAL OFFER PREPARE] Filled offer input for marketplace "
                        + "listing {}. Value={}. No offer has been sent.",
                listing.listingId(),
                enteredValue
        );

        priceInput.press(
                "Tab"
        );

        if (
                isOfferTooLow(
                        page
                )
        ) {

            closeOfferModal(
                    page
            );

            clearPreparedState();

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

        if (
                !submitButton.isEnabled()
        ) {

            throw new IllegalStateException(
                    "Offer submit button is disabled after filling price for "
                            + "marketplace listing "
                            + listing.listingId()
            );
        }

        return true;
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
            Page page
    ) {

        Locator priceInput =
                page.getByTestId(
                                NegotiationSelectors.OFFER_PRICE_INPUT
                        )
                        .first();

        if (
                !priceInput.isVisible()
        ) {

            return;
        }

        page.keyboard()
                .press(
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

        } catch (TimeoutError exception) {

            log.warn(
                    "[REAL OFFER PREPARE] Offer form did not disappear after "
                            + "pressing Escape."
            );
        }
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
                    "Offer submit was attempted, but Vinted did not navigate "
                            + "to an inbox conversation within "
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

        if (
                conversationUrl == null
                        || conversationUrl.isBlank()
                        || !conversationUrl.contains(
                        "/inbox/"
                )
        ) {

            throw new IllegalStateException(
                    "Invalid conversation URL after sending offer: "
                            + conversationUrl
            );
        }

        log.info(
                "[REAL OFFER] Vinted opened conversation for marketplace "
                        + "listing {}. URL: {}",
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

        if (
                path == null
                        || path.isBlank()
        ) {

            throw new IllegalArgumentException(
                    "Conversation URL has no path: "
                            + conversationUrl
            );
        }

        String[] pathParts =
                path.split(
                        "/"
                );

        for (
                int i = 0;
                i < pathParts.length - 1;
                i++
        ) {

            if (
                    "inbox".equals(
                            pathParts[i]
                    )
            ) {

                String conversationId =
                        pathParts[i + 1];

                if (
                        conversationId != null
                                && !conversationId.isBlank()
                ) {

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

        if (
                rawQuery == null
                        || rawQuery.isBlank()
        ) {

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

        if (
                !decodedQuery.contains(
                        listing.listingId()
                )
        ) {

            log.warn(
                    "[REAL OFFER] Conversation URL referrer does not contain "
                            + "marketplace listing ID {}. Decoded query: {}",
                    listing.listingId(),
                    decodedQuery
            );

            return;
        }

        log.info(
                "[REAL OFFER] Conversation referrer matches marketplace "
                        + "listing {}.",
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
                        context.getBot()
                                .getId(),
                        listing.id(),
                        request
                );

        if (
                !"NEGOTIATING".equals(
                        updatedListing.status()
                )
        ) {

            throw new IllegalStateException(
                    "Backend returned unexpected listing status after "
                            + "starting negotiation. Expected NEGOTIATING, "
                            + "actual: "
                            + updatedListing.status()
            );
        }

        if (
                !conversationId.equals(
                        updatedListing.conversationId()
                )
        ) {

            throw new IllegalStateException(
                    "Backend returned unexpected conversation ID. Expected: "
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

            if (
                    expectedPrice.compareTo(
                            actualPrice
                    ) != 0
            ) {

                log.error(
                        "[REAL OFFER] Submitted offer price does not match. "
                                + "Backend listing={}, expected={}, "
                                + "displayed={}, raw='{}'.",
                        listing.id(),
                        expectedPrice,
                        actualPrice,
                        rawPrice
                );

            } else {

                log.info(
                        "[REAL OFFER] Submitted offer price confirmed in "
                                + "conversation: {}.",
                        actualPrice
                );
            }

            logOwnOfferStatus(
                    page
            );

        } catch (Exception exception) {

            log.warn(
                    "[REAL OFFER] Could not fully verify submitted offer "
                            + "inside conversation for marketplace listing {}. "
                            + "Backend remains NEGOTIATING.",
                    listing.listingId()
            );

            log.trace(
                    "[REAL OFFER] Full submitted-offer verification error.",
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
                    "[REAL OFFER] Current own-offer status displayed by "
                            + "Vinted: {}",
                    ownOfferStatus.innerText()
            );

        } catch (TimeoutError exception) {

            log.warn(
                    "[REAL OFFER] Own-offer status did not become visible "
                            + "inside conversation."
            );
        }
    }


    private void sendFirstMessageSafely(
            Page page,
            ListingResponseDto listing,
            String message
    ) {

        if (
                message == null
                        || message.isBlank()
        ) {

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

            if (
                    !message.equals(
                            messageInput.inputValue()
                    )
            ) {

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

            sendButton.click(
                    new Locator.ClickOptions()
                            .setTimeout(
                                    CHAT_ELEMENT_TIMEOUT_MS
                            )
            );

            if (
                    waitForComposerToClear(
                            page,
                            messageInput
                    )
            ) {

                log.info(
                        "[REAL OFFER] Negotiation message was sent for "
                                + "marketplace listing {}.",
                        listing.listingId()
                );

            } else {

                log.warn(
                        "[REAL OFFER] Send button was clicked, but the message "
                                + "input did not clear for listing {}. "
                                + "Manual verification may be required.",
                        listing.listingId()
                );
            }

        } catch (Exception exception) {

            log.error(
                    "[REAL OFFER] Price offer was sent, but the negotiation "
                            + "message could not be sent for marketplace "
                            + "listing {}. Backend remains NEGOTIATING.",
                    listing.listingId()
            );

            log.trace(
                    "[REAL OFFER] Full first-message error.",
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

        while (
                System.currentTimeMillis()
                        < deadline
        ) {

            try {

                if (
                        messageInput.inputValue()
                                .isBlank()
                ) {

                    return true;
                }

            } catch (PlaywrightException exception) {

                return true;
            }

            page.waitForTimeout(
                    MESSAGE_CONFIRMATION_POLL_INTERVAL_MS
            );
        }

        return false;
    }


    private NegotiationStepDto getFirstNegotiationStep() {

        if (
                context.getBot()
                        .getConfiguration()
                        == null
        ) {

            throw new IllegalStateException(
                    "Bot configuration is missing"
            );
        }

        List<NegotiationStepDto> negotiationSteps =
                context.getBot()
                        .getConfiguration()
                        .getNegotiationSteps();

        if (
                negotiationSteps == null
                        || negotiationSteps.isEmpty()
        ) {

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

        if (
                firstStep.getOfferPrice() == null
        ) {

            throw new IllegalStateException(
                    "First negotiation step has no offer price"
            );
        }

        return firstStep;
    }


    private void validateOfferPrice(
            BigDecimal offerPrice
    ) {

        if (
                offerPrice.signum()
                        <= 0
        ) {

            throw new IllegalArgumentException(
                    "Offer price must be greater than zero"
            );
        }
    }


    private void validateListing(
            ListingResponseDto listing
    ) {

        if (
                listing.id() == null
        ) {

            throw new IllegalArgumentException(
                    "Backend listing id cannot be null"
            );
        }

        if (
                listing.listingId() == null
                        || listing.listingId().isBlank()
        ) {

            throw new IllegalArgumentException(
                    "Marketplace listing id cannot be blank"
            );
        }

        if (
                listing.url() == null
                        || listing.url().isBlank()
        ) {

            throw new IllegalArgumentException(
                    "Listing URL cannot be blank"
            );
        }

        if (
                !"DISCOVERED".equals(
                        listing.status()
                )
        ) {

            throw new IllegalArgumentException(
                    "Cannot start negotiation for listing "
                            + listing.id()
                            + " because its status is "
                            + listing.status()
            );
        }
    }


    private boolean isCurrentListingPage(
            Page page,
            String marketplaceListingId
    ) {

        if (
                marketplaceListingId == null
                        || marketplaceListingId.isBlank()
        ) {

            return false;
        }

        try {

            URI uri =
                    URI.create(
                            page.url()
                    );

            String path =
                    uri.getPath();

            if (
                    path == null
            ) {

                return false;
            }

            return path.equals(
                    "/items/" + marketplaceListingId
            )
                    || path.startsWith(
                    "/items/" + marketplaceListingId + "-"
            );

        } catch (Exception exception) {

            return false;
        }
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

            return false;
        }
    }


    private String resolveListingUrl(
            String url
    ) {

        if (
                url == null
                        || url.isBlank()
        ) {

            throw new IllegalArgumentException(
                    "Listing URL cannot be empty"
            );
        }

        if (
                url.startsWith(
                        "https://"
                )
                        || url.startsWith(
                        "http://"
                )
        ) {

            return url;
        }

        if (
                url.startsWith(
                        "/"
                )
        ) {

            return VINTED_BASE_URL
                    + url;
        }

        return VINTED_BASE_URL
                + "/"
                + url;
    }


    private boolean isBelowEstimatedVintedMinimum(
            ListingResponseDto listing,
            BigDecimal offerPrice
    ) {

        BigDecimal listingPrice =
                listing.originalPrice();

        if (
                listingPrice == null
                        || listingPrice.signum()
                        <= 0
        ) {

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

        if (
                offerPrice.compareTo(
                        estimatedMinimum
                ) >= 0
        ) {

            return false;
        }

        log.info(
                "[REAL OFFER PREPARE] Skipping marketplace listing {} "
                        + "before opening it. Listing price={}, "
                        + "configured offer={}, estimated Vinted minimum={}.",
                listing.listingId(),
                listingPrice,
                offerPrice,
                estimatedMinimum
        );

        return true;
    }


    private BigDecimal parsePrice(
            String rawPrice
    ) {

        if (
                rawPrice == null
                        || rawPrice.isBlank()
        ) {

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

        if (
                normalized.contains(
                        ","
                )
                        && normalized.contains(
                        "."
                )
        ) {

            int lastComma =
                    normalized.lastIndexOf(
                            ','
                    );

            int lastDot =
                    normalized.lastIndexOf(
                            '.'
                    );

            if (
                    lastComma > lastDot
            ) {

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

        } else if (
                normalized.contains(
                        ","
                )
        ) {

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


    private void requireMatchingPreparedOffer(
            ListingResponseDto listing
    ) {

        if (
                preparedOffer == null
        ) {

            throw new IllegalStateException(
                    "No prepared first offer exists"
            );
        }

        if (
                !Objects.equals(
                        preparedOffer.backendListingId(),
                        listing.id()
                )
                        || !Objects.equals(
                        preparedOffer.marketplaceListingId(),
                        listing.listingId()
                )
        ) {

            throw new IllegalStateException(
                    "Prepared offer belongs to another listing. Prepared "
                            + "backend listing="
                            + preparedOffer.backendListingId()
                            + ", requested backend listing="
                            + listing.id()
            );
        }
    }


    private void clearPreparedState() {

        preparedOffer =
                null;
    }


    private String normalizeVisibleText(
            String value
    ) {

        if (
                value == null
        ) {

            return "";
        }

        return value
                .trim()
                .replaceAll(
                        "\\s+",
                        " "
                );
    }


    private String getFriendlyErrorMessage(
            Throwable exception
    ) {

        if (
                exception == null
        ) {

            return "Unknown error";
        }

        String message =
                exception.getMessage();

        if (
                message == null
                        || message.isBlank()
        ) {

            return exception
                    .getClass()
                    .getSimpleName();
        }

        int firstLineEnd =
                message.indexOf(
                        '\n'
                );

        if (
                firstLineEnd > 0
        ) {

            return message
                    .substring(
                            0,
                            firstLineEnd
                    )
                    .trim();
        }

        return message.trim();
    }


    private record PreparedOffer(
            Long backendListingId,
            String marketplaceListingId,
            BigDecimal offerPrice,
            Integer stepNumber,
            String message
    ) {
    }
}
