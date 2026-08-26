package pl.flipbot.playwright.negotiation;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Strong post-submit evidence for a real marketplace offer.
 *
 * Navigating to a conversation is useful context, but it is not sufficient to
 * prove that the just-clicked offer was actually accepted by the marketplace.
 * Before backend state may advance, require Vinted to expose our own offer with
 * the exact expected price in the resulting conversation.
 */
@Slf4j
public final class SubmittedOfferConfirmationVerifier {

    private static final double CONFIRMATION_TIMEOUT_MS = 20_000;

    private SubmittedOfferConfirmationVerifier() {
    }

    public static BigDecimal requireExactOwnOffer(
            Page page,
            String marketplaceListingId,
            BigDecimal expectedPrice
    ) {
        Objects.requireNonNull(page, "Page cannot be null");
        Objects.requireNonNull(expectedPrice, "Expected offer price cannot be null");

        if (expectedPrice.signum() <= 0) {
            throw new IllegalArgumentException("Expected offer price must be positive");
        }

        String listingId = marketplaceListingId == null
                ? "<unknown>"
                : marketplaceListingId;

        Locator ownOfferPrice = page.getByTestId(
                        NegotiationSelectors.OWN_OFFER_PRICE
                )
                .last();

        try {
            ownOfferPrice.waitFor(
                    new Locator.WaitForOptions()
                            .setState(WaitForSelectorState.VISIBLE)
                            .setTimeout(CONFIRMATION_TIMEOUT_MS)
            );
        } catch (PlaywrightException exception) {
            throw new IllegalStateException(
                    "Offer submit was clicked for marketplace listing "
                            + listingId
                            + ", but the resulting conversation did not expose a visible own-offer price within "
                            + Math.round(CONFIRMATION_TIMEOUT_MS / 1_000)
                            + " seconds. Delivery is ambiguous; do not retry automatically.",
                    exception
            );
        }

        String rawPrice;
        try {
            rawPrice = ownOfferPrice.innerText();
        } catch (PlaywrightException exception) {
            throw new IllegalStateException(
                    "Offer submit was clicked for marketplace listing "
                            + listingId
                            + ", but the visible own-offer price could not be read. Delivery is ambiguous; do not retry automatically.",
                    exception
            );
        }

        BigDecimal displayedPrice = parsePrice(rawPrice);

        if (displayedPrice.compareTo(expectedPrice) != 0) {
            throw new IllegalStateException(
                    "Offer submit was clicked for marketplace listing "
                            + listingId
                            + ", but Vinted displayed own-offer price "
                            + displayedPrice
                            + " instead of expected "
                            + expectedPrice
                            + ". Delivery is ambiguous; do not retry automatically."
            );
        }

        log.info(
                "[REAL OFFER] Submitted own offer is strongly confirmed in conversation. marketplaceListing={}, price={}.",
                listingId,
                displayedPrice
        );

        return displayedPrice;
    }

    static BigDecimal parsePrice(String rawPrice) {
        if (rawPrice == null || rawPrice.isBlank()) {
            throw new IllegalArgumentException("Price text cannot be blank");
        }

        String normalized = rawPrice
                .replace("\u00A0", "")
                .replace("\u202F", "")
                .replace(" ", "")
                .replaceAll("[^0-9,.-]", "");

        if (normalized.contains(",") && normalized.contains(".")) {
            int lastComma = normalized.lastIndexOf(',');
            int lastDot = normalized.lastIndexOf('.');

            if (lastComma > lastDot) {
                normalized = normalized.replace(".", "").replace(',', '.');
            } else {
                normalized = normalized.replace(",", "");
            }
        } else if (normalized.contains(",")) {
            normalized = normalized.replace(',', '.');
        }

        BigDecimal value = new BigDecimal(normalized);
        if (value.signum() <= 0) {
            throw new IllegalArgumentException("Parsed own-offer price must be positive");
        }

        return value;
    }
}
