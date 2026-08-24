package pl.flipbot.playwright.probe;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.WaitUntilState;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.negotiation.NegotiationSelectors;
import pl.flipbot.playwright.verification.HumanVerificationHandler;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

@Slf4j
public class PriceProbeExecutor {

    private static final double NAVIGATION_TIMEOUT_MS = 30_000;
    private static final double ELEMENT_TIMEOUT_MS = 15_000;
    private static final double CONTACT_DISCOVERY_TIMEOUT_MS = 20_000;
    private static final double COMPOSER_TIMEOUT_MS = 20_000;
    private static final double CONFIRMATION_TIMEOUT_MS = 5_000;
    private static final double POLL_INTERVAL_MS = 250;
    private static final int MAX_DIAGNOSTIC_ACTIONS = 24;

    private static final List<String> MESSAGE_BUTTON_TEST_IDS = List.of(
            "item-buyer-message-button",
            "item-message-seller-button",
            "item-buyer-contact-button",
            "item-message-button",
            "item-contact-seller-button",
            "item-buyer-contact-seller-button",
            "item-contact-button"
    );

    private static final Pattern MESSAGE_BUTTON_LABEL = Pattern.compile(
            "^(Napisz|Napisz wiadomość|Napisz wiadomosc|Wyślij wiadomość|Wyslij wiadomosc|"
                    + "Napisz do sprzedającego|Napisz do sprzedajacego|Napisz do sprzedawcy|"
                    + "Napisz do użytkownika|Napisz do uzytkownika|Zapytaj sprzedającego|"
                    + "Zapytaj sprzedajacego|Zapytaj o przedmiot|Skontaktuj się ze sprzedającym|"
                    + "Skontaktuj sie ze sprzedajacym|Skontaktuj się ze sprzedawcą|"
                    + "Skontaktuj sie ze sprzedawca|Wiadomość|Wiadomosc|Message|Message seller|"
                    + "Contact seller|Ask seller|Send message)$",
            Pattern.CASE_INSENSITIVE
    );

    private final BotContext context;
    private final PriceProbeRuntimeConfig config;
    private final PriceProbeTestHumanPacing humanPacing;
    private final HumanVerificationHandler humanVerificationHandler =
            new HumanVerificationHandler();

    public PriceProbeExecutor(
            BotContext context,
            PriceProbeRuntimeConfig config
    ) {
        this.context = context;
        this.config = config;
        this.humanPacing = PriceProbeTestHumanPacing.fromEnvironment(config);
    }

    public PriceProbeExecutionResult execute(PriceProbeAssignmentDto assignment) {
        Objects.requireNonNull(assignment, "Price probe assignment cannot be null");

        if (!config.enabled()) {
            return PriceProbeExecutionResult.failed("Price probes are disabled.");
        }

        if (!validAssignment(assignment)) {
            return PriceProbeExecutionResult.failed(
                    "Price probe assignment is incomplete or invalid."
            );
        }

        String listingUrl;
        try {
            listingUrl = config.mappedListingUrl(assignment.listingUrl());
        } catch (RuntimeException exception) {
            return PriceProbeExecutionResult.failed(
                    "Could not map source listing onto the configured probe endpoint: "
                            + friendlyMessage(exception)
            );
        }

        Page page = context.getPage();
        boolean sendClickAttempted = false;

        try {
            log.info(
                    "[PRICE PROBE] Opening source listing {} for probe {} / bot {}: {}",
                    assignment.marketplaceListingId(),
                    assignment.probeId(),
                    context.getBot().getId(),
                    listingUrl
            );

            page.navigate(
                    listingUrl,
                    new Page.NavigateOptions()
                            .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                            .setTimeout(NAVIGATION_TIMEOUT_MS)
            );

            if (!config.isAllowedUrl(page.url())) {
                return PriceProbeExecutionResult.failed(
                        "Listing navigation left the configured probe endpoint: "
                                + safeUrl(page)
                );
            }

            humanPacing.afterNavigation(page);
            humanVerificationHandler.waitUntilVerified(page);

            MessageEntryPoint entryPoint = waitForMessageEntryPoint(page);
            Locator composer = entryPoint.composer();

            if (composer == null) {
                Locator messageAction = entryPoint.action();

                if (messageAction == null) {
                    String diagnostics = describeVisibleActions(page);
                    log.warn(
                            "[PRICE PROBE] No message-seller entry point became usable within {} seconds. url={}. Visible actions: {}",
                            Math.round(CONTACT_DISCOVERY_TIMEOUT_MS / 1_000),
                            safeUrl(page),
                            diagnostics
                    );

                    return PriceProbeExecutionResult.failed(
                            "Listing exposes no supported message-seller action after "
                                    + Math.round(CONTACT_DISCOVERY_TIMEOUT_MS / 1_000)
                                    + "s."
                    );
                }

                log.info(
                        "[PRICE PROBE] Opening seller message flow for probe {}. action={}",
                        assignment.probeId(),
                        describeAction(messageAction)
                );

                humanPacing.beforeClick(page, messageAction);
                messageAction.click(
                        new Locator.ClickOptions()
                                .setTimeout(ELEMENT_TIMEOUT_MS)
                );
                humanPacing.afterClick(page);

                humanVerificationHandler.waitUntilVerified(page);
                composer = waitForComposer(page);
            } else {
                log.info(
                        "[PRICE PROBE] Message composer is already visible for probe {}; no listing action click is needed.",
                        assignment.probeId()
                );
            }

            if (!config.isAllowedUrl(page.url())) {
                return PriceProbeExecutionResult.failed(
                        "Message flow left the configured probe endpoint: "
                                + safeUrl(page)
                );
            }

            if (!config.enabled()) {
                return PriceProbeExecutionResult.failed(
                        "PRICE_PROBE was disabled before the send step."
                );
            }

            humanPacing.typeText(
                    page,
                    composer,
                    assignment.message()
            );

            if (!assignment.message().equals(composer.inputValue())) {
                return PriceProbeExecutionResult.failed(
                        "Message composer did not retain the expected probe message."
                );
            }

            Locator sendButton = resolveSendButton(page, composer);

            if (sendButton == null) {
                log.warn(
                        "[PRICE PROBE] Composer is visible but no supported send button was found. url={}. Visible actions: {}",
                        safeUrl(page),
                        describeVisibleActions(page)
                );
                return PriceProbeExecutionResult.failed(
                        "Conversation exposes no supported send button."
                );
            }

            if (!config.enabled()) {
                return PriceProbeExecutionResult.failed(
                        "PRICE_PROBE was disabled immediately before send."
                );
            }

            log.info(
                    "[PRICE PROBE] Sending one text-only probe {} for bot {} / marketplace listing {}. referencePrice={}, probePrice={} PLN.",
                    assignment.probeId(),
                    context.getBot().getId(),
                    assignment.marketplaceListingId(),
                    assignment.referenceOfferPrice(),
                    assignment.probePrice()
            );

            humanPacing.beforeClick(page, sendButton);
            sendClickAttempted = true;
            sendButton.click(
                    new Locator.ClickOptions()
                            .setTimeout(ELEMENT_TIMEOUT_MS)
            );
            humanPacing.afterClick(page);

            if (!waitForComposerToClear(page, composer)) {
                return PriceProbeExecutionResult.unknown(
                        "Send was clicked, but delivery could not be confirmed."
                );
            }

            return PriceProbeExecutionResult.sent();

        } catch (Exception exception) {
            String details = friendlyMessage(exception);

            return sendClickAttempted
                    ? PriceProbeExecutionResult.unknown(details)
                    : PriceProbeExecutionResult.failed(details);
        }
    }

    private boolean validAssignment(PriceProbeAssignmentDto assignment) {
        return assignment.probeId() != null
                && assignment.probeId() > 0
                && assignment.sourceListingBackendId() != null
                && assignment.sourceListingBackendId() > 0
                && assignment.marketplaceListingId() != null
                && !assignment.marketplaceListingId().isBlank()
                && assignment.listingUrl() != null
                && !assignment.listingUrl().isBlank()
                && assignment.referenceOfferPrice() != null
                && assignment.referenceOfferPrice().signum() > 0
                && assignment.probePrice() != null
                && assignment.probePrice().signum() > 0
                && assignment.probePrice().compareTo(assignment.referenceOfferPrice()) < 0
                && assignment.message() != null
                && !assignment.message().isBlank()
                && assignment.message().contains("PLN");
    }

    private MessageEntryPoint waitForMessageEntryPoint(Page page) {
        long deadline = System.currentTimeMillis()
                + (long) CONTACT_DISCOVERY_TIMEOUT_MS;

        while (System.currentTimeMillis() < deadline) {
            humanVerificationHandler.waitUntilVerified(page);

            if (!config.isAllowedUrl(page.url())) {
                return MessageEntryPoint.none();
            }

            Locator composer = visibleComposer(page);
            if (composer != null) {
                return MessageEntryPoint.composer(composer);
            }

            Locator action = findMessageAction(page);
            if (action != null) {
                return MessageEntryPoint.action(action);
            }

            page.waitForTimeout(POLL_INTERVAL_MS);
        }

        return MessageEntryPoint.none();
    }

    private Locator findMessageAction(Page page) {
        for (String testId : MESSAGE_BUTTON_TEST_IDS) {
            try {
                Locator candidate = page.getByTestId(testId).first();
                if (isUsable(candidate)) {
                    return candidate;
                }
            } catch (RuntimeException ignored) {
                // Continue with semantic fallbacks.
            }
        }

        Locator directConversationLink = findDirectConversationLink(page);
        if (directConversationLink != null) {
            return directConversationLink;
        }

        Locator roleButton = page.getByRole(
                AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName(MESSAGE_BUTTON_LABEL)
        ).first();

        if (isUsable(roleButton)) {
            return roleButton;
        }

        Locator roleLink = page.getByRole(
                AriaRole.LINK,
                new Page.GetByRoleOptions().setName(MESSAGE_BUTTON_LABEL)
        ).first();

        if (isUsable(roleLink)) {
            return roleLink;
        }

        return findSemanticActionFallback(page);
    }

    private Locator findDirectConversationLink(Page page) {
        try {
            Locator links = page.locator("a[href*='/inbox/']");
            int count = Math.min(links.count(), 20);

            for (int index = 0; index < count; index++) {
                Locator link = links.nth(index);
                if (!isUsable(link)) {
                    continue;
                }

                String href = safeAttribute(link, "href");
                if (href != null && href.matches(".*?/inbox/[^/?#]+.*")) {
                    return link;
                }
            }
        } catch (RuntimeException ignored) {
            // Continue with other fallbacks.
        }

        return null;
    }

    private Locator findSemanticActionFallback(Page page) {
        try {
            Locator actions = page.locator("button:visible, a:visible");
            int count = Math.min(actions.count(), 80);

            for (int index = 0; index < count; index++) {
                Locator candidate = actions.nth(index);
                if (!isUsable(candidate)) {
                    continue;
                }

                String testId = safeAttribute(candidate, "data-testid");
                if (isContactActionTestId(testId)) {
                    return candidate;
                }

                String ariaLabel = safeAttribute(candidate, "aria-label");
                if (isContactActionLabel(ariaLabel)) {
                    return candidate;
                }

                String text = safeInnerText(candidate);
                if (isContactActionLabel(text)) {
                    return candidate;
                }
            }
        } catch (RuntimeException ignored) {
            // No semantic fallback available.
        }

        return null;
    }

    static boolean isContactActionLabel(String rawLabel) {
        if (rawLabel == null || rawLabel.isBlank()) {
            return false;
        }

        String normalized = rawLabel
                .replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();

        return MESSAGE_BUTTON_LABEL.matcher(normalized).matches();
    }

    static boolean isContactActionTestId(String rawTestId) {
        if (rawTestId == null || rawTestId.isBlank()) {
            return false;
        }

        String testId = rawTestId.trim().toLowerCase(Locale.ROOT);
        boolean contactLike = testId.contains("message")
                || testId.contains("contact")
                || testId.contains("chat");

        if (!contactLike) {
            return false;
        }

        return !testId.contains("send")
                && !testId.contains("composer")
                && !testId.contains("conversation")
                && !testId.contains("offer")
                && !testId.contains("buy")
                && !testId.contains("favorite")
                && !testId.contains("favourite")
                && !testId.contains("share")
                && !testId.contains("login")
                && !testId.contains("register")
                && !testId.contains("header");
    }

    private Locator waitForComposer(Page page) {
        Locator primary = page.getByTestId(
                NegotiationSelectors.MESSAGE_INPUT
        ).first();

        try {
            primary.waitFor(
                    new Locator.WaitForOptions()
                            .setState(WaitForSelectorState.VISIBLE)
                            .setTimeout(COMPOSER_TIMEOUT_MS)
            );
            return primary;
        } catch (TimeoutError exception) {
            Locator fallback = page.locator(
                    "textarea:visible, [contenteditable='true']:visible"
            ).first();
            fallback.waitFor(
                    new Locator.WaitForOptions()
                            .setState(WaitForSelectorState.VISIBLE)
                            .setTimeout(2_500)
            );
            return fallback;
        }
    }

    private Locator visibleComposer(Page page) {
        try {
            Locator primary = page.getByTestId(
                    NegotiationSelectors.MESSAGE_INPUT
            ).first();

            if (isUsable(primary)) {
                return primary;
            }
        } catch (RuntimeException ignored) {
            // Fall through.
        }

        try {
            Locator fallback = page.locator(
                    "textarea:visible, [contenteditable='true']:visible"
            ).first();
            return isUsable(fallback) ? fallback : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private Locator resolveSendButton(Page page, Locator composer) {
        try {
            Locator icon = page.getByTestId(
                    NegotiationSelectors.MESSAGE_SEND_ICON
            ).last();
            Locator button = icon.locator(
                    "xpath=ancestor::button[1]"
            ).first();

            if (isUsable(button)) {
                return button;
            }
        } catch (RuntimeException ignored) {
            // Try form-scoped fallback.
        }

        try {
            Locator form = composer.locator("xpath=ancestor::form[1]").first();
            Locator submit = form.locator("button[type='submit']").first();
            return isUsable(submit) ? submit : null;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private boolean waitForComposerToClear(Page page, Locator composer) {
        long deadline = System.currentTimeMillis()
                + (long) CONFIRMATION_TIMEOUT_MS;

        while (System.currentTimeMillis() < deadline) {
            try {
                if (composer.inputValue().isBlank()) {
                    return true;
                }
            } catch (PlaywrightException exception) {
                return true;
            }

            page.waitForTimeout(POLL_INTERVAL_MS);
        }

        return false;
    }

    private boolean isUsable(Locator locator) {
        try {
            return locator != null
                    && locator.count() > 0
                    && locator.isVisible()
                    && locator.isEnabled();
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private String describeVisibleActions(Page page) {
        try {
            Locator actions = page.locator("button:visible, a:visible");
            int count = Math.min(actions.count(), MAX_DIAGNOSTIC_ACTIONS);
            StringBuilder result = new StringBuilder();

            for (int index = 0; index < count; index++) {
                Locator action = actions.nth(index);
                String description = describeAction(action);

                if (description.isBlank()) {
                    continue;
                }

                if (!result.isEmpty()) {
                    result.append(" | ");
                }
                result.append(description);
            }

            return result.isEmpty() ? "<none>" : result.toString();
        } catch (RuntimeException exception) {
            return "<diagnostics unavailable: " + friendlyMessage(exception) + ">";
        }
    }

    private String describeAction(Locator action) {
        if (action == null) {
            return "<null>";
        }

        String testId = safeAttribute(action, "data-testid");
        String ariaLabel = safeAttribute(action, "aria-label");
        String href = safeAttribute(action, "href");
        String text = safeInnerText(action);

        return "{testId=" + compact(testId)
                + ", aria=" + compact(ariaLabel)
                + ", text=" + compact(text)
                + ", href=" + compact(href)
                + "}";
    }

    private String safeAttribute(Locator locator, String name) {
        try {
            return locator.getAttribute(name);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String safeInnerText(Locator locator) {
        try {
            return locator.innerText();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String compact(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }

        String normalized = value
                .replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();

        return normalized.length() <= 100
                ? normalized
                : normalized.substring(0, 100) + "…";
    }

    private String safeUrl(Page page) {
        try {
            return page == null || page.isClosed()
                    ? "<closed>"
                    : page.url();
        } catch (RuntimeException exception) {
            return "<unavailable>";
        }
    }

    private String friendlyMessage(Throwable throwable) {
        if (throwable == null) {
            return "unknown error";
        }

        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }

        return message.lines().findFirst().orElse(message).trim();
    }

    private record MessageEntryPoint(
            Locator composer,
            Locator action
    ) {
        private static MessageEntryPoint composer(Locator composer) {
            return new MessageEntryPoint(composer, null);
        }

        private static MessageEntryPoint action(Locator action) {
            return new MessageEntryPoint(null, action);
        }

        private static MessageEntryPoint none() {
            return new MessageEntryPoint(null, null);
        }
    }
}
