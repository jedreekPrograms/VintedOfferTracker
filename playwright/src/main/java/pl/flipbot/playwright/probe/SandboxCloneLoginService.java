package pl.flipbot.playwright.probe;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.WaitUntilState;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.login.LoginSelectors;
import pl.flipbot.playwright.verification.HumanVerificationHandler;

@Slf4j
public class SandboxCloneLoginService {

    private static final double NAVIGATION_TIMEOUT_MS = 30_000;
    private static final double ELEMENT_TIMEOUT_MS = 15_000;
    private static final double AUTHENTICATED_TIMEOUT_MS = 45_000;
    private static final double POLL_INTERVAL_MS = 250;

    private static final String REGISTER_SWITCH_TEST_ID =
            "auth-select-type--register-switch";
    private static final String LOGIN_EMAIL_TEST_ID =
            "auth-select-type--login-email";

    private final BotContext context;
    private final PriceProbeRuntimeConfig config;
    private final HumanVerificationHandler humanVerificationHandler =
            new HumanVerificationHandler();

    public SandboxCloneLoginService(
            BotContext context,
            PriceProbeRuntimeConfig config
    ) {
        this.context = context;
        this.config = config;
    }

    public void login() {
        if (!config.enabled()) {
            throw new IllegalStateException(
                    "Sandbox clone login requested while price probes are disabled."
            );
        }

        validateCredentials();

        Page page = context.getPage();

        log.info(
                "[PRICE PROBE LOGIN] Opening sandbox marketplace home for bot {}: {}",
                context.getBot().getId(),
                config.homeUrl()
        );

        page.navigate(
                config.homeUrl(),
                new Page.NavigateOptions()
                        .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout(NAVIGATION_TIMEOUT_MS)
        );

        if (!config.isAllowedUrl(page.url())) {
            throw new IllegalStateException(
                    "Sandbox login navigation left the configured test marketplace: "
                            + page.url()
            );
        }

        acceptCookiesIfVisible(page);
        humanVerificationHandler.waitUntilVerified(page);

        if (isAuthenticated(page)) {
            log.info(
                    "[PRICE PROBE LOGIN] Bot {} already has an authenticated sandbox session.",
                    context.getBot().getId()
            );
            return;
        }

        openAuthentication(page);
        openEmailLoginIfNeeded(page);
        fillAndSubmit(page);
        waitUntilAuthenticated(page);

        context.saveSession();

        log.info(
                "[PRICE PROBE LOGIN] Bot {} authenticated on sandbox marketplace {}.",
                context.getBot().getId(),
                config.baseUri()
        );
    }

    private void validateCredentials() {
        if (context.getBot().getEmail() == null
                || context.getBot().getEmail().isBlank()) {
            throw new IllegalStateException(
                    "Price-probe bot has no sandbox account e-mail configured."
            );
        }

        if (context.getBot().getPassword() == null
                || context.getBot().getPassword().isBlank()) {
            throw new IllegalStateException(
                    "Price-probe bot has no sandbox account password configured."
            );
        }
    }

    private void acceptCookiesIfVisible(Page page) {
        try {
            Locator button = page.locator("#onetrust-accept-btn-handler").first();
            button.waitFor(
                    new Locator.WaitForOptions()
                            .setState(WaitForSelectorState.VISIBLE)
                            .setTimeout(2_500)
            );
            button.click();
        } catch (RuntimeException ignored) {
            // Optional sandbox UI.
        }
    }

    private boolean isAuthenticated(Page page) {
        try {
            Locator conversations = page.getByTestId(
                    LoginSelectors.CONVERSATIONS_BUTTON
            ).first();
            return conversations.isVisible();
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private void openAuthentication(Page page) {
        Locator loginButton = page.getByTestId(
                LoginSelectors.LOGIN_BUTTON
        ).first();

        loginButton.waitFor(
                new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(ELEMENT_TIMEOUT_MS)
        );
        loginButton.click();
    }

    private void openEmailLoginIfNeeded(Page page) {
        Locator emailInput = page.locator(
                "#" + LoginSelectors.EMAIL_INPUT
        ).first();

        long deadline = System.currentTimeMillis()
                + (long) ELEMENT_TIMEOUT_MS;

        while (System.currentTimeMillis() < deadline) {
            if (isVisible(emailInput)) {
                return;
            }

            Locator switchToLogin = page.getByTestId(
                    REGISTER_SWITCH_TEST_ID
            ).first();

            if (isVisible(switchToLogin)) {
                switchToLogin.click();
                page.waitForTimeout(POLL_INTERVAL_MS);
                continue;
            }

            Locator emailLogin = page.getByTestId(
                    LOGIN_EMAIL_TEST_ID
            ).first();

            if (isVisible(emailLogin)) {
                emailLogin.click();
                page.waitForTimeout(POLL_INTERVAL_MS);
                continue;
            }

            page.waitForTimeout(POLL_INTERVAL_MS);
        }

        throw new IllegalStateException(
                "Sandbox clone did not expose the e-mail login form."
        );
    }

    private void fillAndSubmit(Page page) {
        Locator emailInput = page.locator(
                "#" + LoginSelectors.EMAIL_INPUT
        ).first();
        Locator passwordInput = page.locator(
                "#" + LoginSelectors.PASSWORD_INPUT
        ).first();

        emailInput.waitFor(
                new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(ELEMENT_TIMEOUT_MS)
        );
        passwordInput.waitFor(
                new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(ELEMENT_TIMEOUT_MS)
        );

        emailInput.fill(context.getBot().getEmail());
        passwordInput.fill(context.getBot().getPassword());

        if (!context.getBot().getEmail().equals(emailInput.inputValue())) {
            throw new IllegalStateException(
                    "Sandbox login e-mail field did not retain the configured value."
            );
        }

        Locator form = emailInput.locator(
                "xpath=ancestor::form[1]"
        ).first();

        Locator submit = form.locator(
                "button[type='submit']"
        ).first();

        submit.waitFor(
                new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(ELEMENT_TIMEOUT_MS)
        );

        if (!submit.isEnabled()) {
            throw new IllegalStateException(
                    "Sandbox login submit button is disabled after credentials were filled."
            );
        }

        submit.click();
        humanVerificationHandler.waitUntilVerified(page);
    }

    private void waitUntilAuthenticated(Page page) {
        long deadline = System.currentTimeMillis()
                + (long) AUTHENTICATED_TIMEOUT_MS;

        while (System.currentTimeMillis() < deadline) {
            humanVerificationHandler.waitUntilVerified(page);

            if (!config.isAllowedUrl(page.url())) {
                throw new IllegalStateException(
                        "Sandbox login redirected outside the configured test marketplace: "
                                + page.url()
                );
            }

            if (isAuthenticated(page)) {
                return;
            }

            page.waitForTimeout(POLL_INTERVAL_MS);
        }

        throw new IllegalStateException(
                "Sandbox marketplace did not expose an authenticated session within "
                        + Math.round(AUTHENTICATED_TIMEOUT_MS / 1_000)
                        + " seconds."
        );
    }

    private boolean isVisible(Locator locator) {
        try {
            return locator != null && locator.count() > 0 && locator.isVisible();
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
