package pl.flipbot.playwright.negotiation;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.model.NegotiationStepDto;
import pl.flipbot.playwright.verification.HumanVerificationHandler;

import java.math.BigDecimal;

/**
 * Fail-closed proof of a NEW own offer in a conversation after the submit click.
 *
 * No second submit is attempted if the DOM update is late or ambiguous. The
 * original bounded 30-second wait and baseline count remain unchanged. The
 * price persisted by the caller is the actually displayed Vinted price.
 */
@Slf4j
final class NextStepOwnOfferConfirmation {
    private static final double OFFER_CONFIRMATION_TIMEOUT_MS = 30_000;
    private static final double OFFER_CONFIRMATION_POLL_INTERVAL_MS = 500;

    private final HumanVerificationHandler humanVerificationHandler;

    NextStepOwnOfferConfirmation(HumanVerificationHandler humanVerificationHandler) {
        this.humanVerificationHandler = humanVerificationHandler;
    }

    SubmittedOffer waitForNewOwnOffer(
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

    private BigDecimal parsePrice(String rawPrice) {
        return VintedPriceParser.parseNextStepConfirmation(rawPrice);
    }

    record SubmittedOffer(BigDecimal displayedPrice, String rawStatus) {
    }
}
