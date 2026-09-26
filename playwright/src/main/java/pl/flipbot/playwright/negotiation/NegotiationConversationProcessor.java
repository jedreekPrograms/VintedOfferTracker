package pl.flipbot.playwright.negotiation;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.marketplace.MarketplaceNavigator;
import pl.flipbot.playwright.verification.HumanVerificationHandler;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;

@Slf4j
@RequiredArgsConstructor
public class NegotiationConversationProcessor {

    private static final double CONVERSATION_STATE_TIMEOUT_MS =
            20_000;

    private static final double POLL_INTERVAL_MS =
            500;

    private static final String OWN_OFFER_STATUS_TEST_ID =
            "offer-status-title";

    private static final String SELLER_COUNTER_OFFER_PRICE_TEST_ID =
            "offer-current-price-label";

    private final BotContext context;

    private final HumanVerificationHandler humanVerificationHandler =
            new HumanVerificationHandler();

    private ConversationOfferModalPriceInspector offerModalPriceInspector() {
        return new ConversationOfferModalPriceInspector(context);
    }

    /*
     * Tymczasowo zostawiamy starą metodę, żeby obecny BotWorker
     * nadal się kompilował.
     *
     * W następnym kroku BotWorker zacznie korzystać bezpośrednio
     * z inspectSnapshot().
     */
    public NegotiationConversationResult inspect(
            ListingResponseDto listing
    ) {

        NegotiationConversationSnapshot snapshot =
                inspectSnapshot(
                        listing
                );

        /*
         * Stary BotWorker nie obsługuje jeszcze ceny kontroferty.
         * Dlatego na jeden krok przejściowy zwracamy UNKNOWN.
         */
        if (snapshot.result()
                == NegotiationConversationResult.SELLER_COUNTER_OFFER) {

            log.warn(
                    "[CONVERSATION] Seller counteroffer {} was detected "
                            + "for listing {}, but the current BotWorker "
                            + "does not process snapshots yet.",
                    snapshot.sellerCounterOfferPrice(),
                    listing.listingId()
            );

            return NegotiationConversationResult.UNKNOWN;

        }

        return snapshot.result();

    }

    public NegotiationConversationSnapshot inspectSnapshot(
            ListingResponseDto listing
    ) {

        Objects.requireNonNull(
                listing,
                "Listing cannot be null"
        );

        validateListing(
                listing
        );

        Page page =
                context.getPage();

        log.info(
                "[CONVERSATION] Opening conversation {} "
                        + "for backend listing {}, marketplace listing {}",
                listing.conversationId(),
                listing.id(),
                listing.listingId()
        );

        new MarketplaceNavigator(context).goToTrustedVintedUrl(
                listing.conversationUrl()
        );

        humanVerificationHandler.waitUntilVerified(
                page
        );

        ListingResponseDto canonicalListing =
                validateOpenedConversation(
                        page,
                        listing
                );

        NegotiationConversationSnapshot snapshot =
                waitForConversationSnapshot(
                        page,
                        canonicalListing
                );

        logSnapshot(
                canonicalListing,
                snapshot
        );

        return snapshot;

    }

    private NegotiationConversationSnapshot waitForConversationSnapshot(
            Page page,
            ListingResponseDto listing
    ) {

        long deadline =
                System.currentTimeMillis()
                        + (long) CONVERSATION_STATE_TIMEOUT_MS;

        while (System.currentTimeMillis() < deadline) {

            humanVerificationHandler.waitUntilVerified(
                    page
            );

            NegotiationConversationSnapshot snapshot =
                    readLatestNegotiationEvent(
                            page,
                            listing
                    );

            /*
             * A stable, visible own-offer status is useful evidence even when
             * we do not yet know its business semantics. Returning that raw
             * status immediately lets the later availability detector inspect
             * the page instead of logging the same unsupported label every
             * 500 ms for the full 20-second timeout. UNKNOWN remains fail-safe:
             * the decision layer sends no follow-up action for it.
             */
            if (snapshot.result()
                    != NegotiationConversationResult.UNKNOWN
                    || (snapshot.rawStatus() != null
                    && !snapshot.rawStatus().isBlank())) {

                return snapshot;

            }

            page.waitForTimeout(
                    POLL_INTERVAL_MS
            );

        }

        log.warn(
                "[CONVERSATION] Could not recognize the latest negotiation "
                        + "event within {} seconds. "
                        + "Conversation: {}, marketplace listing: {}",
                Math.round(
                        CONVERSATION_STATE_TIMEOUT_MS / 1_000
                ),
                listing.conversationId(),
                listing.listingId()
        );

        return NegotiationConversationSnapshot.unknown();

    }

    private NegotiationConversationSnapshot readLatestNegotiationEvent(
            Page page,
            ListingResponseDto listing
    ) {

        try {

            Locator conversationContent =
                    page.getByTestId(
                                    "conversation-content"
                            )
                            .first();

            if (!conversationContent.isVisible()) {

                return NegotiationConversationSnapshot.unknown();

            }

            /*
             * Locator z selektorem rozdzielonym przecinkiem zwraca
             * oba typy elementów w kolejności ich wystąpienia w DOM.
             */
            Locator negotiationEvents =
                    conversationContent.locator(
                            "[data-testid='"
                                    + OWN_OFFER_STATUS_TEST_ID
                                    + "'], "
                                    + "[data-testid='"
                                    + SELLER_COUNTER_OFFER_PRICE_TEST_ID
                                    + "']"
                    );

            int eventsCount =
                    negotiationEvents.count();

            if (eventsCount == 0) {

                return NegotiationConversationSnapshot.unknown();

            }

            /*
             * Idziemy od końca, ponieważ interesuje nas najnowsze
             * widoczne zdarzenie negocjacyjne.
             */
            for (int index = eventsCount - 1;
                 index >= 0;
                 index--) {

                Locator event =
                        negotiationEvents.nth(
                                index
                        );

                if (!event.isVisible()) {
                    continue;
                }

                String testId =
                        event.getAttribute(
                                "data-testid"
                        );

                String rawText =
                        event.innerText();

                if (SELLER_COUNTER_OFFER_PRICE_TEST_ID.equals(
                        testId
                )) {

                    /*
                     * Vinted reuses offer-current-price-label in more than one
                     * price-bearing UI fragment. Treat it as a real seller
                     * counteroffer only when the price belongs to the same
                     * message card as Vinted's seller-offer buy action.
                     *
                     * Without this scope check a decorative/current-price
                     * label can appear after our own offer status in DOM order
                     * and incorrectly win the "latest event" scan.
                     */
                    if (!isSellerCounterOfferCard(event)) {
                        log.debug(
                                "[CONVERSATION] Ignoring unscoped '{}' price label for listing {} because no seller-offer buy action exists in the same conversation card. Raw text={}",
                                SELLER_COUNTER_OFFER_PRICE_TEST_ID,
                                listing.listingId(),
                                rawText
                        );
                        continue;
                    }

                    BigDecimal counterOfferPrice =
                            VintedPriceParser.parse(
                                    rawText
                            );

                    BigDecimal validationCeiling = listing.originalPrice();

                    if (!isPlausibleSellerCounterOffer(
                            validationCeiling,
                            counterOfferPrice
                    )) {
                        /*
                         * originalPrice is a historical snapshot. The seller
                         * may have edited the listing price since negotiation
                         * started. Opening "Zaproponuj cenę" is read-only until
                         * submit; Vinted exposes the CURRENT item price in that
                         * modal ("Cena przedmiotu: ...", also as the input
                         * placeholder). Use it only as a validation ceiling.
                         */
                        BigDecimal liveItemPrice =
                                offerModalPriceInspector()
                                        .readCurrentItemPrice()
                                        .orElse(null);

                        if (isPlausibleSellerCounterOffer(
                                liveItemPrice,
                                counterOfferPrice
                        )) {
                            validationCeiling = liveItemPrice;
                            log.warn(
                                    "[CONVERSATION] Seller counteroffer {} for listing {} is above the captured original price {}, but is valid against Vinted's current item price {} read from the offer modal. Treating the stored original price as stale.",
                                    counterOfferPrice,
                                    listing.listingId(),
                                    listing.originalPrice(),
                                    liveItemPrice
                            );
                        } else {
                            log.error(
                                    "[CONVERSATION] Ignoring implausible seller counteroffer for listing {}. Raw price={}, parsed={}, captured original price={}, current item price from offer modal={}. Returning UNKNOWN so no price-based action can be sent from ambiguous DOM evidence.",
                                    listing.listingId(),
                                    rawText,
                                    counterOfferPrice,
                                    listing.originalPrice(),
                                    liveItemPrice
                            );

                            return NegotiationConversationSnapshot.unknown(
                                    rawText
                            );
                        }
                    }

                    log.info(
                            "[CONVERSATION] Latest negotiation event is "
                                    + "a seller counteroffer. Raw price: {}, "
                                    + "parsed price: {}, validation ceiling: {}",
                            rawText,
                            counterOfferPrice,
                            validationCeiling
                    );

                    return NegotiationConversationSnapshot
                            .sellerCounterOffer(
                                    counterOfferPrice
                            );

                }

                if (OWN_OFFER_STATUS_TEST_ID.equals(
                        testId
                )) {

                    log.info(
                            "[CONVERSATION] Latest negotiation event is "
                                    + "an own-offer status: {}",
                            rawText
                    );

                    return createStatusSnapshot(
                            rawText
                    );

                }

            }

            return NegotiationConversationSnapshot.unknown();

        } catch (PlaywrightException exception) {

            log.debug(
                    "Conversation DOM changed while reading "
                            + "the latest negotiation event",
                    exception
            );

            return NegotiationConversationSnapshot.unknown();

        } catch (IllegalArgumentException exception) {

            log.warn(
                    "[CONVERSATION] Could not parse the latest "
                            + "negotiation event",
                    exception
            );

            return NegotiationConversationSnapshot.unknown();

        }

    }

    NegotiationConversationSnapshot createStatusSnapshot(
            String rawStatus
    ) {

        String normalizedStatus =
                normalizeStatus(
                        rawStatus
                );

        /*
         * Vinted has used multiple Polish grammatical forms for the same
         * offer state across UI variants (for example "Zaakceptowane",
         * "Zaakceptowana" and "Zaakceptowano"). Matching the stable word
         * stem keeps those presentation changes from silently leaving a
         * genuinely accepted offer in NEGOTIATING.
         */
        if (normalizedStatus.contains(
                "oczekuj"
        )) {

            return NegotiationConversationSnapshot.pending(
                    rawStatus
            );

        }

        if (!normalizedStatus.contains(
                "niezaakceptowan"
        ) && normalizedStatus.contains(
                "zaakceptowan"
        )) {

            return NegotiationConversationSnapshot.accepted(
                    rawStatus
            );

        }

        if (normalizedStatus.contains(
                "odrzucon"
        )) {

            return NegotiationConversationSnapshot.rejected(
                    rawStatus
            );

        }

        log.warn(
                "[CONVERSATION] Unsupported own-offer status: {}. Returning UNKNOWN without polling the same stable label for 20 seconds; availability checks will still run and no follow-up offer will be sent from UNKNOWN state.",
                rawStatus
        );

        return NegotiationConversationSnapshot.unknown(
                rawStatus
        );

    }

    static boolean isPlausibleSellerCounterOffer(
            BigDecimal originalPrice,
            BigDecimal counterOfferPrice
    ) {
        if (counterOfferPrice == null
                || counterOfferPrice.signum() <= 0) {
            return false;
        }

        if (originalPrice == null
                || originalPrice.signum() <= 0) {
            return true;
        }

        return counterOfferPrice.compareTo(originalPrice) <= 0;
    }

    private boolean isSellerCounterOfferCard(
            Locator priceLabel
    ) {
        try {
            Object result = priceLabel.evaluate(
                    """
                    node => {
                        const root = node.closest(
                            '[data-testid="conversation-content"]'
                        );

                        let current = node.parentElement;

                        while (current && current !== root) {
                            if (current.querySelector(
                                '[data-testid="offer-message-buy-button"]'
                            )) {
                                return true;
                            }
                            current = current.parentElement;
                        }

                        return false;
                    }
                    """
            );

            return Boolean.TRUE.equals(result);
        } catch (PlaywrightException exception) {
            log.debug(
                    "[CONVERSATION] Could not scope seller-price label to its offer card: {}",
                    exception.getMessage()
            );
            return false;
        }
    }

    private void logSnapshot(
            ListingResponseDto listing,
            NegotiationConversationSnapshot snapshot
    ) {

        if (snapshot.result()
                == NegotiationConversationResult.SELLER_COUNTER_OFFER) {

            log.info(
                    "[CONVERSATION] Conversation {} for listing {} "
                            + "was classified as SELLER_COUNTER_OFFER. "
                            + "Seller price: {}",
                    listing.conversationId(),
                    listing.listingId(),
                    snapshot.sellerCounterOfferPrice()
            );

            return;

        }

        log.info(
                "[CONVERSATION] Conversation {} for listing {} "
                        + "was classified as {}. Raw status: {}",
                listing.conversationId(),
                listing.listingId(),
                snapshot.result(),
                snapshot.rawStatus()
        );

    }

    private String normalizeStatus(
            String status
    ) {

        if (status == null) {
            return "";
        }

        return status
                .toLowerCase(
                        Locale.ROOT
                )
                .replace(
                        "ą",
                        "a"
                )
                .replace(
                        "ć",
                        "c"
                )
                .replace(
                        "ę",
                        "e"
                )
                .replace(
                        "ł",
                        "l"
                )
                .replace(
                        "ń",
                        "n"
                )
                .replace(
                        "ó",
                        "o"
                )
                .replace(
                        "ś",
                        "s"
                )
                .replace(
                        "ź",
                        "z"
                )
                .replace(
                        "ż",
                        "z"
                )
                .trim();

    }

    private ListingResponseDto validateOpenedConversation(
            Page page,
            ListingResponseDto listing
    ) {
        ListingResponseDto canonicalListing =
                new ConversationIdentityCoordinator(context)
                        .verifyAndCanonicalize(
                                listing,
                                "Opened conversation"
                        );

        log.info(
                "[CONVERSATION] Opened expected conversation {}",
                canonicalListing.conversationId()
        );

        return canonicalListing;
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
                    "Conversation can only be inspected for a NEGOTIATING "
                            + "listing. Backend listing: "
                            + listing.id()
                            + ", current status: "
                            + listing.status()
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

}