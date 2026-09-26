package pl.flipbot.playwright.negotiation;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.context.BotContext;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Read-only inspection of Vinted's "Zaproponuj cenę" modal.
 *
 * Opening the modal does not submit an offer. This class never fills the input
 * and never touches offer-submit-button. It is used only to obtain Vinted's
 * current "Cena przedmiotu" when a historical originalPrice snapshot looks
 * stale while validating a seller counteroffer.
 */
@Slf4j
@RequiredArgsConstructor
public class ConversationOfferModalPriceInspector {

    private static final String OFFER_MODAL_TEST_ID =
            "offer-modal";

    private static final String OFFER_MODAL_CLOSE_BUTTON_TEST_ID =
            "offer-modal-navigation-close-button";

    private static final double MODAL_TIMEOUT_MS = 4_000;

    private static final Pattern ITEM_PRICE_PATTERN =
            Pattern.compile(
                    "Cena\\s+przedmiotu\\s*:\\s*([^\\n\\r]+)",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
            );

    private final BotContext context;

    public Optional<BigDecimal> readCurrentItemPrice() {
        if (context == null || context.getPage() == null) {
            return Optional.empty();
        }

        Page page = context.getPage();
        Locator offerButton = page.getByTestId(
                        NegotiationSelectors.CHAT_OFFER_BUTTON
                )
                .first();
        Locator modal = page.getByTestId(
                        OFFER_MODAL_TEST_ID
                )
                .first();

        try {
            if (!offerButton.isVisible() || !offerButton.isEnabled()) {
                log.debug(
                        "[OFFER MODAL PRICE] Cannot read current item price because the conversation has no enabled offer action."
                );
                return Optional.empty();
            }

            if (!modal.isVisible()) {
                offerButton.click();

                modal.waitFor(
                        new Locator.WaitForOptions()
                                .setState(WaitForSelectorState.VISIBLE)
                                .setTimeout(MODAL_TIMEOUT_MS)
                );
            }

            Optional<BigDecimal> fromLabel =
                    parseItemPriceFromModalText(modal.innerText());

            if (fromLabel.isPresent()) {
                log.info(
                        "[OFFER MODAL PRICE] Read current Vinted item price {} from the 'Cena przedmiotu' label. No offer was submitted.",
                        fromLabel.get()
                );
                return fromLabel;
            }

            Locator priceInput = modal.getByTestId(
                            NegotiationSelectors.OFFER_PRICE_INPUT
                    )
                    .first();

            if (priceInput.isVisible()) {
                String placeholder = priceInput.getAttribute("placeholder");

                if (placeholder != null && !placeholder.isBlank()) {
                    BigDecimal parsed = VintedPriceParser.parse(placeholder);
                    log.info(
                            "[OFFER MODAL PRICE] Read current Vinted item price {} from the offer-input placeholder fallback. No offer was submitted.",
                            parsed
                    );
                    return Optional.of(parsed);
                }
            }

            log.warn(
                    "[OFFER MODAL PRICE] Offer modal opened, but Vinted exposed neither a readable 'Cena przedmiotu' label nor price placeholder."
            );
            return Optional.empty();
        } catch (Exception exception) {
            log.warn(
                    "[OFFER MODAL PRICE] Could not read the current item price from Vinted's offer modal: {}",
                    friendly(exception)
            );
            return Optional.empty();
        } finally {
            closeModalSafely(page, modal);
        }
    }

    static Optional<BigDecimal> parseItemPriceFromModalText(
            String modalText
    ) {
        if (modalText == null || modalText.isBlank()) {
            return Optional.empty();
        }

        Matcher matcher = ITEM_PRICE_PATTERN.matcher(modalText);

        if (!matcher.find()) {
            return Optional.empty();
        }

        String priceText = matcher.group(1);

        /*
         * Match only the first line/value after "Cena przedmiotu:". The modal
         * also contains total-combined-offer-price (item + Buyer Protection),
         * which must never be used as the listing price.
         */
        int newline = priceText.indexOf('\n');
        if (newline >= 0) {
            priceText = priceText.substring(0, newline);
        }

        try {
            return Optional.of(VintedPriceParser.parse(priceText));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private void closeModalSafely(
            Page page,
            Locator modal
    ) {
        try {
            if (!modal.isVisible()) {
                return;
            }

            Locator closeButton = page.getByTestId(
                            OFFER_MODAL_CLOSE_BUTTON_TEST_ID
                    )
                    .first();

            if (closeButton.isVisible()) {
                closeButton.click();
            } else {
                page.keyboard().press("Escape");
            }

            try {
                modal.waitFor(
                        new Locator.WaitForOptions()
                                .setState(WaitForSelectorState.HIDDEN)
                                .setTimeout(MODAL_TIMEOUT_MS)
                );
            } catch (TimeoutError ignored) {
                log.warn(
                        "[OFFER MODAL PRICE] Offer modal did not disappear within {} ms after the read-only inspection.",
                        Math.round(MODAL_TIMEOUT_MS)
                );
            }
        } catch (Exception exception) {
            log.debug(
                    "[OFFER MODAL PRICE] Could not close offer modal cleanly: {}",
                    friendly(exception)
            );
        }
    }

    private String friendly(Throwable throwable) {
        if (throwable == null
                || throwable.getMessage() == null
                || throwable.getMessage().isBlank()) {
            return throwable == null
                    ? "unknown"
                    : throwable.getClass().getSimpleName();
        }

        String message = throwable.getMessage();
        int newline = message.indexOf('\n');

        return newline > 0
                ? message.substring(0, newline).trim()
                : message.trim();
    }
}
