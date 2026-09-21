package pl.flipbot.playwright.negotiation;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.context.BotContext;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

@Slf4j
@RequiredArgsConstructor
public class ConversationAvailabilityDetector {

    private static final String STATUS_MESSAGE_TEST_ID =
            "conversation-message--status-message";

    private static final String CONVERSATION_CONTENT_TEST_ID =
            "conversation-content";

    private static final List<String> UNAVAILABLE_PHRASES =
            List.of(
                    "przedmiot jest niedostepny",
                    "przedmiot zostal sprzedany lub usuniety",
                    "item is unavailable",
                    "item has been sold or removed",
                    "item has been sold or deleted"
            );

    private final BotContext context;

    public boolean isUnavailable(
            ListingResponseDto listing
    ) {
        if (listing == null
                || listing.conversationId() == null
                || listing.conversationId().isBlank()) {
            return false;
        }

        Page page = context.getPage();

        if (!isExpectedConversationOpen(
                page,
                listing.conversationId()
        )) {
            /*
             * Never classify a listing from content belonging to another
             * conversation.
             */
            return false;
        }

        try {
            Locator statusMessages =
                    page.getByTestId(
                            STATUS_MESSAGE_TEST_ID
                    );

            int count = statusMessages.count();

            for (int index = 0; index < count; index++) {
                Locator statusMessage =
                        statusMessages.nth(
                                index
                        );

                if (!statusMessage.isVisible()) {
                    continue;
                }

                String rawText = statusMessage.innerText();

                if (containsUnavailableEvidence(rawText)) {
                    logUnavailable(
                            listing,
                            "status message",
                            rawText
                    );
                    return true;
                }
            }

            /*
             * Vinted does not always render the sold/removed banner under the
             * stable status-message test id. Inspect the currently opened
             * conversation panel as a second, conversation-scoped source.
             * This catches the visible:
             * "Przedmiot jest niedostępny / Przedmiot został sprzedany lub
             * usunięty" banner without looking at sidebar conversations.
             */
            Locator conversationContent =
                    page.getByTestId(
                                    CONVERSATION_CONTENT_TEST_ID
                            )
                            .first();

            if (conversationContent.isVisible()) {
                String rawConversationText =
                        conversationContent.innerText();

                if (containsUnavailableEvidence(rawConversationText)) {
                    logUnavailable(
                            listing,
                            "conversation content",
                            rawConversationText
                    );
                    return true;
                }
            }

            return false;
        } catch (Exception exception) {
            /*
             * Availability detection is a safety guard. If DOM inspection
             * itself fails, do not guess a terminal status here; the separate
             * negotiation-action availability guard still runs afterwards.
             */
            log.debug(
                    "[AVAILABILITY] Could not inspect availability status "
                            + "for marketplace listing {} in conversation {}.",
                    listing.listingId(),
                    listing.conversationId()
            );

            log.trace(
                    "[AVAILABILITY] Full availability detection exception.",
                    exception
            );

            return false;
        }
    }

    static boolean containsUnavailableEvidence(
            String rawText
    ) {
        String normalized = normalize(rawText);

        if (normalized.isBlank()) {
            return false;
        }

        return UNAVAILABLE_PHRASES.stream()
                .anyMatch(normalized::contains);
    }

    private void logUnavailable(
            ListingResponseDto listing,
            String source,
            String rawText
    ) {
        log.warn(
                "[AVAILABILITY] Vinted reports marketplace listing {} "
                        + "as sold/removed/unavailable in conversation {}. "
                        + "Evidence source={}. Text={}",
                listing.listingId(),
                listing.conversationId(),
                source,
                compact(rawText)
        );
    }

    private boolean isExpectedConversationOpen(
            Page page,
            String conversationId
    ) {
        String currentUrl = page.url();

        if (currentUrl == null
                || currentUrl.isBlank()) {
            return false;
        }

        return currentUrl.contains(
                "/inbox/" + conversationId
        );
    }

    private static String normalize(
            String value
    ) {
        if (value == null) {
            return "";
        }

        return Normalizer.normalize(
                        value,
                        Normalizer.Form.NFD
                )
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String compact(
            String value
    ) {
        if (value == null) {
            return "";
        }

        String compacted = value.replaceAll(
                        "\\s+",
                        " "
                )
                .trim();

        if (compacted.length() <= 240) {
            return compacted;
        }

        return compacted.substring(
                0,
                240
        ) + "...";
    }
}
