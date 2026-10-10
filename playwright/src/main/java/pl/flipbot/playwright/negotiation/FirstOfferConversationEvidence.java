package pl.flipbot.playwright.negotiation;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.verification.HumanVerificationHandler;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Conversation URL handling after the first real offer. Does not send offers,
 * update backend state or assume an ambiguous submission succeeded.
 *
 * The referrer check used after a successful submit stays advisory; the
 * independent fail-closed reconciliation still requires visible own-price proof.
 */
@Slf4j
final class FirstOfferConversationEvidence {

    private static final double CONVERSATION_TIMEOUT_MS = 30_000;

    private FirstOfferConversationEvidence() {
    }

    static String waitForConversationUrl(
            Page page,
            ListingResponseDto listing,
            HumanVerificationHandler humanVerificationHandler
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

    static String extractConversationId(
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

    static void validateConversationReferrer(
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

    static String decodedQuery(String conversationUrl) {
        URI conversationUri = URI.create(conversationUrl);
        String rawQuery = conversationUri.getRawQuery();
        return rawQuery == null
                ? ""
                : URLDecoder.decode(rawQuery, StandardCharsets.UTF_8);
    }
}
