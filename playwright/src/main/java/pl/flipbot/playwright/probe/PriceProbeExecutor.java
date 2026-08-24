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
import java.util.Objects;
import java.util.regex.Pattern;

@Slf4j
public class PriceProbeExecutor {

    private static final double NAVIGATION_TIMEOUT_MS = 30_000;
    private static final double ELEMENT_TIMEOUT_MS = 15_000;
    private static final double COMPOSER_TIMEOUT_MS = 20_000;
    private static final double CONFIRMATION_TIMEOUT_MS = 5_000;
    private static final double POLL_INTERVAL_MS = 250;

    private static final List<String> MESSAGE_BUTTON_TEST_IDS = List.of(
            "item-buyer-message-button",
            "item-message-seller-button",
            "item-buyer-contact-button",
            "item-message-button"
    );

    private static final Pattern MESSAGE_BUTTON_LABEL = Pattern.compile(
            "^(Napisz wiadomość|Napisz wiadomosc|Wyślij wiadomość|Wyslij wiadomosc|Wiadomość|Wiadomosc|Message|Message seller|Contact seller|Ask seller)$",
            Pattern.CASE_INSENSITIVE
    );

    private final BotContext context;
    private final PriceProbeRuntimeConfig config;
    private final HumanVerificationHandler humanVerificationHandler =
            new HumanVerificationHandler();

    public PriceProbeExecutor(
            BotContext context,
            PriceProbeRuntimeConfig config
    ) {
        this.context = context;
        this.config = config;
    }

    /**
     * Sends exactly one text-only sandbox probe. This executor never clicks
     * item-buyer-offer-button, make-offer-request-button or offer-submit-button.
     */
    public PriceProbeExecutionResult execute(
            PriceProbeAssignmentDto assignment
    ) {
        Objects.requireNonNull(assignment, "Price probe assignment cannot be null");

        if (!config.enabled()) {
            return PriceProbeExecutionResult.failed(
                    "Sandbox price probes are disabled."
            );
        }

        if (!validAssignment(assignment)) {
            return PriceProbeExecutionResult.failed(
                    "Price probe assignment is incomplete or invalid."
            );
        }

        if (!config.isAllowedUrl(assignment.listingUrl())) {
            return PriceProbeExecutionResult.failed(
                    "Assignment URL is outside the configured sandbox marketplace."
            );
        }

        Page page = context.getPage();
        boolean sendClickAttempted = false;

        try {
            log.info(
                    "[PRICE PROBE] Bot {} opening sandbox listing {} for probe {} ({}/{}). referencePrice={}, probePrice={}.",
                    context.getBot().getId(),
                    assignment.marketplaceListingId(),
                    assignment.probeId(),
                    assignment.probeNumber(),
                    assignment.maximumProbeCount(),
                    assignment.referenceOfferPrice(),
                    assignment.probePrice()
            );

            page.navigate(
                    assignment.listingUrl(),
                    new Page.NavigateOptions()
                            .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                            .setTimeout(NAVIGATION_TIMEOUT_MS)
            );

            if (!config.isAllowedUrl(page.url())) {
                return PriceProbeExecutionResult.failed(
                        "Listing navigation left the configured sandbox marketplace: "
                                + safeUrl(page)
                );
            }

            humanVerificationHandler.waitUntilVerified(page);

            Locator composer = visibleComposer(page);

            if (composer == null) {
                Locator messageButton = findMessageButton(page);

                if (messageButton == null) {
                    return PriceProbeExecutionResult.failed(
                            "Sandbox listing exposes no supported message-seller action."
                    );
                }

                log.info(
                        "[PRICE PROBE] Opening text conversation for marketplace listing {}. No price-offer control will be used.",
                        assignment.marketplaceListingId()
                );

                messageButton.click(
                        new Locator.ClickOptions()
                                .setTimeout(ELEMENT_TIMEOUT_MS)
                );

                humanVerificationHandler.waitUntilVerified(page);
                composer = waitForComposer(page);
            }

            if (!config.isAllowedUrl(page.url())) {
                return PriceProbeExecutionResult.failed(
                        "Message flow left the configured sandbox marketplace: "
                                + safeUrl(page)
                );
            }

            composer.fill(assignment.message());

            if (!assignment.message().equals(composer.inputValue())) {
                return PriceProbeExecutionResult.failed(
                        "Sandbox message composer did not retain the expected probe message."
                );
            }

            Locator sendButton = resolveSendButton(page, composer);

            if (sendButton == null) {
                return PriceProbeExecutionResult.failed(
                        "Sandbox conversation exposes no supported send button."
                );
            }

            log.warn(
                    "[PRICE PROBE] Sending ONE text-only sandbox probe for bot {} / marketplace listing {}: probePrice={} PLN. Official offer controls are not touched.",
                    context.getBot().getId(),
                    assignment.marketplaceListingId(),
                    assignment.probePrice()
            );

            /*
             * From this point delivery is ambiguous if Playwright throws. The
             * backend claim has already been persisted, so UNKNOWN is never
             * automatically retried for this bot/listing pair.
             */
            sendClickAttempted = true;
            sendButton.click(
                    new Locator.ClickOptions()
                            .setTimeout(ELEMENT_TIMEOUT_MS)
            );

            if (!waitForComposerToClear(page, composer)) {
                return PriceProbeExecutionResult.unknown(
                        "Send was clicked, but the composer did not clear within the confirmation window."
                );
            }

            log.info(
                    "[PRICE PROBE] Text-only sandbox probe {} confirmed for bot {} / marketplace listing {}.",
                    assignment.probeId(),
                    context.getBot().getId(),
                    assignment.marketplaceListingId()
            );

            return PriceProbeExecutionResult.sent();

        } catch (Exception exception) {
            String details = friendlyMessage(exception);

            log.warn(
                    "[PRICE PROBE] Probe {} for bot {} / marketplace listing {} failed. sendClickAttempted={}, reason={}",
                    assignment.probeId(),
                    context.getBot().getId(),
                    assignment.marketplaceListingId(),
                    sendClickAttempted,
                    details
            );

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
                && assignment.probePrice() != null
                && assignment.probePrice().signum() > 0
                && assignment.message() != null
                && !assignment.message().isBlank()
                && assignment.message().contains("PLN");
    }

    private Locator findMessageButton(Page page) {
        for (String testId : MESSAGE_BUTTON_TEST_IDS) {
            try {
                Locator candidate = page.getByTestId(testId).first();
                if (candidate.count() > 0
                        && candidate.isVisible()
                        && candidate.isEnabled()) {
                    log.info(
                            "[PRICE PROBE] Found message-seller action by data-testid='{}'.",
                            testId
                    );
                    return candidate;
                }
            } catch (RuntimeException ignored) {
                // Continue with the next strict selector.
            }
        }

        Locator roleButton = page.getByRole(
                AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName(MESSAGE_BUTTON_LABEL)
        ).first();

        if (isUsable(roleButton)) {
            log.info(
                    "[PRICE PROBE] Found message-seller action by accessible button label."
            );
            return roleButton;
        }

        Locator roleLink = page.getByRole(
                AriaRole.LINK,
                new Page.GetByRoleOptions().setName(MESSAGE_BUTTON_LABEL)
        ).first();

        if (isUsable(roleLink)) {
            log.info(
                    "[PRICE PROBE] Found message-seller action by accessible link label."
            );
            return roleLink;
        }

        return null;
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
                    "textarea:visible"
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

            if (primary.count() > 0 && primary.isVisible()) {
                return primary;
            }
        } catch (RuntimeException ignored) {
            // Fall through.
        }

        return null;
    }

    private Locator resolveSendButton(
            Page page,
            Locator composer
    ) {
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
            // Try a form-scoped fallback.
        }

        try {
            Locator form = composer.locator(
                    "xpath=ancestor::form[1]"
            ).first();
            Locator submit = form.locator(
                    "button[type='submit']"
            ).first();

            return isUsable(submit) ? submit : null;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private boolean waitForComposerToClear(
            Page page,
            Locator composer
    ) {
        long deadline = System.currentTimeMillis()
                + (long) CONFIRMATION_TIMEOUT_MS;

        while (System.currentTimeMillis() < deadline) {
            try {
                if (composer.inputValue().isBlank()) {
                    return true;
                }
            } catch (PlaywrightException exception) {
                /* DOM replacement after submit also proves the old composer left. */
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

        return message.lines()
                .findFirst()
                .orElse(message)
                .trim();
    }
}
