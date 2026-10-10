package pl.flipbot.playwright.negotiation;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.AriaRole;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.marketplace.MarketplaceNavigator;
import pl.flipbot.playwright.verification.HumanVerificationHandler;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Readiness checks BEFORE a first offer can be prepared and BEFORE quota is
 * reserved. Controls trusted listing navigation, page availability and exact
 * offer-button discovery; never clicks submit or writes negotiation state.
 *
 * Reuses original observed Vinted selectors, bounded waits and safe fallbacks.
 */
@Slf4j
final class FirstOfferListingReadiness {

    private static final String VINTED_BASE_URL =
            "https://www.vinted.pl";

    private static final String ITEM_TITLE_SELECTOR =
            "[data-testid='item-page-summary-plugin'] h1";

    private static final Pattern OFFER_BUTTON_NAME =
            Pattern.compile(
                    "^(Zaproponuj cenę|Make an offer)$",
                    Pattern.CASE_INSENSITIVE
            );

    private static final double LISTING_STATE_TIMEOUT_MS =
            15_000;

    private static final double LISTING_STATE_POLL_INTERVAL_MS =
            250;

    private static final double OFFER_BUTTON_TIMEOUT_MS =
            15_000;


    private final BotContext context;
    private final HumanVerificationHandler humanVerificationHandler;

    FirstOfferListingReadiness(BotContext context,
                               HumanVerificationHandler humanVerificationHandler) {
        this.context = context;
        this.humanVerificationHandler = humanVerificationHandler;
    }

    void navigateToListingIfNeeded(
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

        new MarketplaceNavigator(context).goToTrustedVintedUrl(
                listingUrl
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

    boolean waitForListingPage(
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

    Locator waitForOfferButtonOrNull(
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

    boolean isListingUnavailable(
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
}
