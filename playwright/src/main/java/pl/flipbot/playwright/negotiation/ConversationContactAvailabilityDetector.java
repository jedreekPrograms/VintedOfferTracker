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
public class ConversationContactAvailabilityDetector {

    private static final String STATUS_MESSAGE_TEST_ID =
            "conversation-message--status-message";

    private static final int SETTLE_ATTEMPTS = 10;
    private static final double SETTLE_DELAY_MS = 500;

    private static final List<String> EXPLICIT_CONTACT_UNAVAILABLE_PHRASES =
            List.of(
                    "nie mozesz wysylac wiadomosci",
                    "nie mozesz wyslac wiadomosci",
                    "nie mozna wysylac wiadomosci",
                    "nie mozesz skontaktowac sie",
                    "nie mozna skontaktowac sie",
                    "zablokowal cie",
                    "you cannot send messages",
                    "you can't send messages",
                    "you cant send messages",
                    "unable to send messages",
                    "this member has blocked you",
                    "messaging is disabled"
            );

    private final BotContext context;

    public ConversationContactAssessment inspect(
            ListingResponseDto listing
    ) {
        if (listing == null
                || listing.conversationId() == null
                || listing.conversationId().isBlank()) {
            return ConversationContactAssessment.available(
                    "No conversation is available to classify."
            );
        }

        Page page = context.getPage();

        if (!isExpectedConversationOpen(page, listing.conversationId())) {
            return ConversationContactAssessment.available(
                    "The expected conversation is not open, so contact availability is not classified."
            );
        }

        String explicitReason = findExplicitUnavailableReason(page);
        if (explicitReason != null) {
            return ConversationContactAssessment.confirmed(explicitReason);
        }

        for (int attempt = 1; attempt <= SETTLE_ATTEMPTS; attempt++) {
            ContactSignals signals = readSignals(page);
            ConversationContactAssessment.State state = classify(
                    signals.offerButtonVisible(),
                    signals.messageInputVisible(),
                    signals.messageInputEnabled(),
                    false
            );

            if (state == ConversationContactAssessment.State.AVAILABLE) {
                return ConversationContactAssessment.available(
                        "Conversation interaction controls are available. "
                                + signals.describe()
                );
            }

            explicitReason = findExplicitUnavailableReason(page);
            if (explicitReason != null) {
                return ConversationContactAssessment.confirmed(explicitReason);
            }

            if (attempt < SETTLE_ATTEMPTS) {
                page.waitForTimeout(SETTLE_DELAY_MS);
            }
        }

        ContactSignals finalSignals = readSignals(page);

        return ConversationContactAssessment.suspected(
                "The expected conversation is loaded, but neither a usable message composer "
                        + "nor the offer action became available after "
                        + (SETTLE_ATTEMPTS * SETTLE_DELAY_MS / 1000.0)
                        + " seconds. "
                        + finalSignals.describe()
        );
    }

    static ConversationContactAssessment.State classify(
            boolean offerButtonVisible,
            boolean messageInputVisible,
            boolean messageInputEnabled,
            boolean explicitUnavailableSignal
    ) {
        if (explicitUnavailableSignal) {
            return ConversationContactAssessment.State.CONFIRMED_UNAVAILABLE;
        }

        if (offerButtonVisible
                || (messageInputVisible && messageInputEnabled)) {
            return ConversationContactAssessment.State.AVAILABLE;
        }

        return ConversationContactAssessment.State.SUSPECTED_UNAVAILABLE;
    }

    private ContactSignals readSignals(Page page) {
        Locator offerButton = page.getByTestId(
                        NegotiationSelectors.CHAT_OFFER_BUTTON
                )
                .first();

        Locator messageInput = page.getByTestId(
                        NegotiationSelectors.MESSAGE_INPUT
                )
                .first();

        boolean offerButtonVisible = isVisibleSafely(offerButton);
        boolean messageInputVisible = isVisibleSafely(messageInput);
        boolean messageInputEnabled = messageInputVisible
                && isEnabledSafely(messageInput);

        return new ContactSignals(
                offerButtonVisible,
                messageInputVisible,
                messageInputEnabled
        );
    }

    private String findExplicitUnavailableReason(Page page) {
        try {
            Locator statusMessages = page.getByTestId(
                    STATUS_MESSAGE_TEST_ID
            );

            int count = statusMessages.count();
            for (int index = 0; index < count; index++) {
                Locator statusMessage = statusMessages.nth(index);
                if (!statusMessage.isVisible()) {
                    continue;
                }

                String raw = statusMessage.innerText();
                String normalized = normalize(raw);

                for (String phrase : EXPLICIT_CONTACT_UNAVAILABLE_PHRASES) {
                    if (normalized.contains(phrase)) {
                        log.warn(
                                "[CONTACT AVAILABILITY] Explicit contact-unavailable message detected: {}",
                                raw
                        );
                        return "Vinted explicitly reports that contact is unavailable: "
                                + raw.replaceAll("\\s+", " ").trim();
                    }
                }
            }

            Locator conversationButtons = page.locator(
                    "[data-testid='conversation-content'] button"
            );

            int buttonCount = conversationButtons.count();
            for (int index = 0; index < buttonCount; index++) {
                Locator button = conversationButtons.nth(index);
                if (!button.isVisible()) {
                    continue;
                }

                String normalized = normalize(button.innerText());
                if ("odblokuj".equals(normalized)
                        || "unblock".equals(normalized)
                        || normalized.startsWith("odblokuj ")
                        || normalized.startsWith("unblock ")) {
                    return "The conversation exposes an Unblock action, so messaging/negotiation is currently disabled.";
                }
            }

            return null;
        } catch (Exception exception) {
            log.debug(
                    "[CONTACT AVAILABILITY] Explicit block/contact message inspection failed: {}",
                    exception.getClass().getSimpleName()
            );
            return null;
        }
    }

    private boolean isExpectedConversationOpen(
            Page page,
            String conversationId
    ) {
        String url = page.url();
        return url != null
                && !url.isBlank()
                && url.contains("/inbox/" + conversationId);
    }

    private boolean isVisibleSafely(Locator locator) {
        try {
            return locator.isVisible();
        } catch (Exception exception) {
            return false;
        }
    }

    private boolean isEnabledSafely(Locator locator) {
        try {
            return locator.isEnabled();
        } catch (Exception exception) {
            return false;
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }

        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replace('’', '\'')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private record ContactSignals(
            boolean offerButtonVisible,
            boolean messageInputVisible,
            boolean messageInputEnabled
    ) {
        String describe() {
            return "offerButtonVisible=" + offerButtonVisible
                    + ", messageInputVisible=" + messageInputVisible
                    + ", messageInputEnabled=" + messageInputEnabled;
        }
    }
}
