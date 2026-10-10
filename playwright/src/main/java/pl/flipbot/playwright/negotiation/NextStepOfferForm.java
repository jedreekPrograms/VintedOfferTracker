package pl.flipbot.playwright.negotiation;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.model.NegotiationStepDto;
import pl.flipbot.playwright.verification.HumanVerificationHandler;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * UI-only preparation for a later negotiation step, before real submit.
 *
 * The owner executor still controls conversation identity, real submission,
 * own-offer confirmation, backend persistence, quotas and message delivery.
 * This helper preserves both the bounded live-minimum adjustment and the
 * fail-closed outcome when Vinted does not provide a trustworthy minimum.
 */
@Slf4j
final class NextStepOfferForm {

    private static final double ELEMENT_TIMEOUT_MS = 15_000;
    private static final double FORM_OPEN_TIMEOUT_MS = 5_000;
    private static final double OFFER_VALIDATION_TIMEOUT_MS = 1_500;
    private static final double MODAL_CLOSE_TIMEOUT_MS = 3_000;

    private static final Pattern VINTED_MINIMUM_PRICE_PATTERN =
            Pattern.compile(
                    "Minimalna\\s+wartość\\s+nie\\s+może\\s+być\\s+niższa\\s+niż\\s*([0-9\\s.,\\u00A0\\u202F]+)",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
            );


    private final BotContext context;
    private final HumanVerificationHandler humanVerificationHandler;

    NextStepOfferForm(BotContext context,
                      HumanVerificationHandler humanVerificationHandler) {
        this.context = context;
        this.humanVerificationHandler = humanVerificationHandler;
    }

    void openOfferModal(
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

    private boolean waitForOfferForm(Locator priceInput) {
        return OfferFormVisibility.waitUntilVisible(priceInput, FORM_OPEN_TIMEOUT_MS);
    }

    boolean fillOfferPrice(
            Page page,
            ListingResponseDto listing,
            NegotiationStepDto nextStep,
            String logPrefix
    ) {
        humanVerificationHandler.waitUntilVerified(page);

        Locator priceInput = page.getByTestId(NegotiationSelectors.OFFER_PRICE_INPUT).first();
        String expectedPrice = nextStep.getOfferPrice().toPlainString();
        String actualInputValue = OfferPriceFormFields.fillAndVerify(
                priceInput, expectedPrice, ELEMENT_TIMEOUT_MS,
                "Offer input contains an unexpected value. Expected: "
        );

        log.info(
                "{} Filled offer input for listing {}. Expected: {}, actual: {}",
                logPrefix, listing.listingId(), expectedPrice, actualInputValue
        );

        priceInput.press("Tab");

        OfferPriceValidation validation = inspectOfferPriceValidation(page, logPrefix);
        if (validation.tooLow()) {
            boolean recovered = tryRaiseAdaptiveStepToLiveMinimum(
                    page, listing, nextStep, validation, priceInput, logPrefix
            );

            if (!recovered) {
                closeOfferModal(page, logPrefix);
                return false;
            }
        }

        OfferPriceFormFields.requireEnabledSubmit(
                page, ELEMENT_TIMEOUT_MS,
                "Offer submit button is disabled after entering price "
                        + expectedPrice + " for marketplace listing " + listing.listingId()
        );
        log.info("{} Submit button is visible and enabled.", logPrefix);
        return true;
    }

    private OfferPriceValidation inspectOfferPriceValidation(
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
                            .setState(WaitForSelectorState.VISIBLE)
                            .setTimeout(OFFER_VALIDATION_TIMEOUT_MS)
            );

            String message = errorMessage.innerText();
            Optional<BigDecimal> minimum =
                    parseMinimumAllowedPrice(message);

            log.warn(
                    "{} Vinted rejected the configured price as too low. Validation message: {}. Parsed live minimum: {}",
                    logPrefix,
                    message,
                    minimum.map(BigDecimal::toPlainString).orElse("unknown")
            );

            return new OfferPriceValidation(
                    true,
                    minimum.orElse(null)
            );
        } catch (TimeoutError exception) {
            return new OfferPriceValidation(false, null);
        }
    }

    private boolean tryRaiseAdaptiveStepToLiveMinimum(
            Page page,
            ListingResponseDto listing,
            NegotiationStepDto nextStep,
            OfferPriceValidation validation,
            Locator priceInput,
            String logPrefix
    ) {
        if (validation.minimumAllowedPrice() == null
                || context.getBot() == null
                || context.getBot().getConfiguration() == null) {
            return false;
        }

        AdaptiveNegotiationPricingService pricingService =
                new AdaptiveNegotiationPricingService();

        Optional<NegotiationStepDto> adjusted =
                pricingService.raiseEffectiveStepToVintedMinimum(
                        nextStep,
                        validation.minimumAllowedPrice(),
                        context.getBot().getConfiguration()
                );

        if (adjusted.isEmpty()) {
            return false;
        }

        String adjustedPrice =
                adjusted.get().getOfferPrice().toPlainString();

        priceInput.fill(adjustedPrice);

        if (!adjustedPrice.equals(priceInput.inputValue())) {
            throw new IllegalStateException(
                    "Adaptive Vinted-minimum retry input mismatch. Expected: "
                            + adjustedPrice
                            + ", actual: "
                            + priceInput.inputValue()
            );
        }

        log.warn(
                "{} Retrying step {} for listing {} at Vinted live minimum {}. This is still bounded by the global cap.",
                logPrefix,
                nextStep.getStepNumber(),
                listing.listingId(),
                adjustedPrice
        );

        priceInput.press("Tab");

        OfferPriceValidation retryValidation =
                inspectOfferPriceValidation(
                        page,
                        logPrefix
                );

        return !retryValidation.tooLow();
    }

    static Optional<BigDecimal> parseMinimumAllowedPrice(
            String validationMessage
    ) {
        if (validationMessage == null
                || validationMessage.isBlank()) {
            return Optional.empty();
        }

        Matcher matcher =
                VINTED_MINIMUM_PRICE_PATTERN.matcher(
                        validationMessage
                );

        if (!matcher.find()) {
            return Optional.empty();
        }

        try {
            return Optional.of(
                    VintedPriceParser.parse(
                            matcher.group(1)
                    )
            );
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
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

    private record OfferPriceValidation(
            boolean tooLow,
            BigDecimal minimumAllowedPrice
    ) {
    }
}
