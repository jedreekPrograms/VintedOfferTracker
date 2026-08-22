package pl.flipbot.playwright.negotiation;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.WaitUntilState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.verification.HumanVerificationHandler;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Locale;
import java.util.Objects;

@Slf4j
@RequiredArgsConstructor
public class NegotiationConversationProcessor {

    private static final double NAVIGATION_TIMEOUT_MS =
            30_000;

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

        NegotiationConversationSnapshot snapshot =
                waitForConversationSnapshot(
                        page,
                        listing
                );

        logSnapshot(
                listing,
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
                            page
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
            Page page
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

                    BigDecimal counterOfferPrice =
                            parsePrice(
                                    rawText
                            );

                    log.info(
                            "[CONVERSATION] Latest negotiation event is "
                                    + "a seller counteroffer. Raw price: {}, "
                                    + "parsed price: {}",
                            rawText,
                            counterOfferPrice
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

    private NegotiationConversationSnapshot createStatusSnapshot(
            String rawStatus
    ) {

        String normalizedStatus =
                normalizeStatus(
                        rawStatus
                );

        if (normalizedStatus.contains(
                "oczekujace"
        )) {

            return NegotiationConversationSnapshot.pending(
                    rawStatus
            );

        }

        if (normalizedStatus.contains(
                "zaakceptowane"
        )) {

            return NegotiationConversationSnapshot.accepted(
                    rawStatus
            );

        }

        if (normalizedStatus.contains(
                "odrzucone"
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

    private BigDecimal parsePrice(
            String rawPrice
    ) {

        if (rawPrice == null
                || rawPrice.isBlank()) {

            throw new IllegalArgumentException(
                    "Counteroffer price text cannot be blank"
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
                    "Counteroffer price contains no numeric value: "
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
                    "Counteroffer price must be greater than zero: "
                            + rawPrice
            );

        }

        return price;

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
                    "Opened unexpected conversation. Expected: "
                            + listing.conversationId()
                            + ", actual: "
                            + openedConversationId
                            + ", URL: "
                            + currentUrl
            );

        }

        log.info(
                "[CONVERSATION] Opened expected conversation {}",
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
