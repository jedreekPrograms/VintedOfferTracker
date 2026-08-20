package pl.flipbot.playwright.login;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.marketplace.MarketplaceNavigator;
import pl.flipbot.playwright.marketplace.MarketplaceUrls;

import java.net.URI;

@Slf4j
@RequiredArgsConstructor
public class LoginService {

    private static final double AUTH_VIEW_TIMEOUT_MS = 20_000;
    private static final double AUTH_POLL_INTERVAL_MS = 200;
    private static final double AUTH_SWITCH_RETRY_DELAY_MS = 600;
    private static final double POST_LOGIN_TIMEOUT_MS = 60_000;
    private static final int POST_LOGIN_STABLE_FALLBACK_POLLS = 4;
    private static final int MAX_REGISTER_SWITCH_ATTEMPTS = 6;

    private static final String REGISTER_VIEW_TEST_ID =
            "select-type-register-view";
    private static final String LOGIN_VIEW_TEST_ID =
            "select-type-login-view";
    private static final String REGISTER_SWITCH_TEST_ID =
            "auth-select-type--register-switch";
    private static final String LOGIN_EMAIL_TEST_ID =
            "auth-select-type--login-email";
    private static final String REGISTER_SELECT_TYPE_PATH =
            "/member/register/select_type";

    private final BotContext context;

    public void login() {
        Page page = context.getPage();

        hideAutomation(page);

        log.info("[LOGIN] Opening Vinted homepage for bot {}.", context.getBot().getId());
        new MarketplaceNavigator(context).goToHome();
        page.waitForLoadState();

        acceptCookiesIfVisible(page);

        String existingSignal = authenticatedSignal(page, true);
        if (existingSignal != null) {
            log.info(
                    "[LOGIN] Bot {} is already logged in. signal={}",
                    context.getBot().getId(),
                    existingSignal
            );
            return;
        }

        performLogin();
    }

    private void hideAutomation(Page page) {
        page.context().addInitScript(
                "delete Object.getPrototypeOf(navigator).webdriver;"
        );
    }

    private void acceptCookiesIfVisible(Page page) {
        try {
            Locator button =
                    page.locator("#onetrust-accept-btn-handler");

            button.waitFor(
                    new Locator.WaitForOptions()
                            .setState(WaitForSelectorState.VISIBLE)
                            .setTimeout(5_000)
            );

            log.info("[LOGIN] Accepting Vinted cookie banner.");
            button.click();
        } catch (Exception exception) {
            log.debug("[LOGIN] Cookie banner not displayed.");
        }
    }

    private void performLogin() {
        Page page = context.getPage();

        log.info(
                "[LOGIN] Starting interactive login for bot {}.",
                context.getBot().getId()
        );

        openLoginWindow(page);
        openEmailLogin(page);
        fillCredentials(page);
        submitLogin(page);

        String authenticatedBy = waitForAuthenticatedSession(page);

        context.saveSession();

        log.info(
                "[LOGIN] Bot {} logged in successfully. verifiedBy={}",
                context.getBot().getId(),
                authenticatedBy
        );
    }

    private String waitForAuthenticatedSession(Page page) {
        long deadline =
                System.currentTimeMillis()
                        + (long) POST_LOGIN_TIMEOUT_MS;

        int stableFallbackPolls = 0;

        while (System.currentTimeMillis() <= deadline) {
            String strongSignal = authenticatedSignal(page, false);

            if (strongSignal != null) {
                return strongSignal;
            }

            if (looksLikeCompletedLoginWithoutKnownHeaderSelector(page)) {
                stableFallbackPolls++;

                if (stableFallbackPolls >= POST_LOGIN_STABLE_FALLBACK_POLLS) {
                    return "login controls and auth form disappeared on a trusted Vinted page"
                            + " for "
                            + POST_LOGIN_STABLE_FALLBACK_POLLS
                            + " consecutive checks";
                }
            } else {
                stableFallbackPolls = 0;
            }

            page.waitForTimeout(AUTH_POLL_INTERVAL_MS);
        }

        log.error(
                "[LOGIN] Login submission could not be verified within {}s for bot {}. Current URL: {}. The session will NOT be saved as authenticated.",
                Math.round(POST_LOGIN_TIMEOUT_MS / 1_000),
                context.getBot().getId(),
                page.url()
        );
        logVisibleTestIds(page);

        throw new IllegalStateException(
                "Vinted login submission could not be verified. Current URL: "
                        + page.url()
        );
    }

    private String authenticatedSignal(
            Page page,
            boolean allowStableControlAbsenceFallback
    ) {
        Locator conversationsButtons =
                page.getByTestId(LoginSelectors.CONVERSATIONS_BUTTON);

        if (hasVisible(conversationsButtons)) {
            return "header-conversations-button";
        }

        Locator visibleInboxLinks =
                page.locator("a[href*='/inbox']:visible");

        if (visibleInboxLinks.count() > 0) {
            return "visible /inbox link";
        }

        if (allowStableControlAbsenceFallback
                && looksLikeCompletedLoginWithoutKnownHeaderSelector(page)) {
            return "login controls absent on trusted Vinted page";
        }

        return null;
    }

    private boolean looksLikeCompletedLoginWithoutKnownHeaderSelector(Page page) {
        if (!MarketplaceUrls.isVintedUrl(page.url())
                || isAuthenticationUrl(page.url())) {
            return false;
        }

        Locator loginButton = page.getByTestId(LoginSelectors.LOGIN_BUTTON);
        Locator emailInput = page.locator("#" + LoginSelectors.EMAIL_INPUT);
        Locator passwordInput = page.locator("#" + LoginSelectors.PASSWORD_INPUT);
        Locator registerView = page.getByTestId(REGISTER_VIEW_TEST_ID);
        Locator loginView = page.getByTestId(LOGIN_VIEW_TEST_ID);
        Locator switchToLogin = page.getByTestId(REGISTER_SWITCH_TEST_ID);
        Locator emailLogin = page.getByTestId(LOGIN_EMAIL_TEST_ID);

        return !hasVisible(loginButton)
                && !hasVisible(emailInput)
                && !hasVisible(passwordInput)
                && !hasVisible(registerView)
                && !hasVisible(loginView)
                && !hasVisible(switchToLogin)
                && !hasVisible(emailLogin);
    }

    private boolean isAuthenticationUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }

        try {
            URI uri = URI.create(url);
            String path = uri.getPath();

            if (path == null) {
                return false;
            }

            String normalizedPath = path.toLowerCase();
            return normalizedPath.startsWith("/member/register")
                    || normalizedPath.startsWith("/member/login")
                    || normalizedPath.startsWith("/member/auth");
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private boolean hasVisible(Locator locator) {
        try {
            int count = locator.count();

            for (int index = 0; index < count; index++) {
                if (locator.nth(index).isVisible()) {
                    return true;
                }
            }
        } catch (RuntimeException exception) {
            return false;
        }

        return false;
    }

    private void openLoginWindow(Page page) {
        Locator loginButton =
                page.getByTestId(LoginSelectors.LOGIN_BUTTON);

        loginButton.waitFor(
                new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(10_000)
        );

        log.info("[LOGIN] Opening authentication window.");
        loginButton.click();
    }

    private void openEmailLogin(Page page) {
        Locator emailInput =
                page.locator("#" + LoginSelectors.EMAIL_INPUT);
        Locator registerView =
                page.getByTestId(REGISTER_VIEW_TEST_ID);
        Locator loginView =
                page.getByTestId(LOGIN_VIEW_TEST_ID);
        Locator switchToLogin =
                page.getByTestId(REGISTER_SWITCH_TEST_ID);
        Locator emailLogin =
                page.getByTestId(LOGIN_EMAIL_TEST_ID);

        long deadline =
                System.currentTimeMillis()
                        + (long) AUTH_VIEW_TIMEOUT_MS;

        int registerSwitchAttempts = 0;

        while (System.currentTimeMillis() < deadline) {
            if (isVisible(emailInput)) {
                log.info("[LOGIN] E-mail login form is visible.");
                return;
            }

            if (isLoginSelectionVisible(loginView, emailLogin)) {
                log.info("[LOGIN] Login selection view detected.");

                if (isVisible(emailLogin)) {
                    log.info("[LOGIN] Selecting e-mail login.");

                    if (clickEmailLoginAndWait(
                            page,
                            emailLogin,
                            emailInput
                    )) {
                        return;
                    }
                }

                page.waitForTimeout(AUTH_POLL_INTERVAL_MS);
                continue;
            }

            if (isRegistrationSelectionVisible(
                    page,
                    registerView,
                    switchToLogin
            )) {
                if (registerSwitchAttempts >= MAX_REGISTER_SWITCH_ATTEMPTS) {
                    page.waitForTimeout(AUTH_POLL_INTERVAL_MS);
                    continue;
                }

                if (!waitForVisible(
                        switchToLogin,
                        5_000
                )) {
                    log.debug(
                            "[LOGIN] Registration screen detected, but login switch is not visible yet."
                    );
                    page.waitForTimeout(AUTH_POLL_INTERVAL_MS);
                    continue;
                }

                registerSwitchAttempts++;

                log.info(
                        "[LOGIN] Registration selection is visible. Switching to login view. Attempt {}/{}.",
                        registerSwitchAttempts,
                        MAX_REGISTER_SWITCH_ATTEMPTS
                );

                String href = safeAttribute(switchToLogin, "href");

                if (clickRegistrationSwitchAndWait(
                        page,
                        registerView,
                        loginView,
                        switchToLogin,
                        emailLogin,
                        emailInput,
                        href
                )) {
                    continue;
                }

                log.info(
                        "[LOGIN] Authentication view is still unchanged after attempt {}/{}. Retrying.",
                        registerSwitchAttempts,
                        MAX_REGISTER_SWITCH_ATTEMPTS
                );

                page.waitForTimeout(AUTH_SWITCH_RETRY_DELAY_MS);
                continue;
            }

            page.waitForTimeout(AUTH_POLL_INTERVAL_MS);
        }

        log.error(
                "[LOGIN] Could not reach Vinted e-mail login form. Current URL: {}. Register switch attempts: {}.",
                page.url(),
                registerSwitchAttempts
        );

        logVisibleTestIds(page);

        throw new IllegalStateException(
                "Vinted authentication flow could not reach the e-mail login form."
        );
    }

    private boolean clickEmailLoginAndWait(
            Page page,
            Locator emailLogin,
            Locator emailInput
    ) {
        try {
            emailLogin.click();
        } catch (Exception exception) {
            log.info(
                    "[LOGIN] Normal e-mail-login click failed; DOM fallback will be tried. reason={}",
                    friendlyMessage(exception)
            );
        }

        page.waitForTimeout(AUTH_SWITCH_RETRY_DELAY_MS);

        if (isVisible(emailInput)) {
            log.info(
                    "[LOGIN] E-mail login form appeared after normal click."
            );
            return true;
        }

        try {
            log.info(
                    "[LOGIN] Normal e-mail-login click did not open the form. Trying DOM click."
            );
            emailLogin.evaluate("element => element.click()");
        } catch (Exception exception) {
            log.warn(
                    "[LOGIN] DOM e-mail-login click failed: {}",
                    friendlyMessage(exception)
            );
        }

        page.waitForTimeout(AUTH_SWITCH_RETRY_DELAY_MS);

        if (isVisible(emailInput)) {
            log.info(
                    "[LOGIN] E-mail login form appeared after DOM click."
            );
            return true;
        }

        return false;
    }

    private boolean clickRegistrationSwitchAndWait(
            Page page,
            Locator registerView,
            Locator loginView,
            Locator switchToLogin,
            Locator emailLogin,
            Locator emailInput,
            String href
    ) {
        try {
            switchToLogin.click();
        } catch (Exception exception) {
            log.info(
                    "[LOGIN] Normal login-switch click failed; DOM fallback will be tried. reason={}",
                    friendlyMessage(exception)
            );
        }

        page.waitForTimeout(AUTH_SWITCH_RETRY_DELAY_MS);

        if (hasAuthenticationViewChanged(
                page,
                registerView,
                loginView,
                switchToLogin,
                emailLogin,
                emailInput
        )) {
            log.info(
                    "[LOGIN] Authentication view changed after normal click."
            );
            return true;
        }

        log.info(
                "[LOGIN] Normal click did not change authentication view. Trying DOM click."
        );

        try {
            switchToLogin.evaluate("element => element.click()");
        } catch (Exception exception) {
            log.warn(
                    "[LOGIN] DOM login-switch click failed: {}",
                    friendlyMessage(exception)
            );
        }

        page.waitForTimeout(AUTH_SWITCH_RETRY_DELAY_MS);

        if (hasAuthenticationViewChanged(
                page,
                registerView,
                loginView,
                switchToLogin,
                emailLogin,
                emailInput
        )) {
            log.info(
                    "[LOGIN] Authentication view changed after DOM click."
            );
            return true;
        }

        if (!isNavigableHref(href)) {
            return false;
        }

        String resolvedUrl = resolveUrl(page.url(), href);

        if (!MarketplaceUrls.isVintedUrl(resolvedUrl)) {
            log.warn(
                    "[LOGIN] Refusing authentication href fallback outside trusted Vinted hosts: {}",
                    resolvedUrl
            );
            return false;
        }

        log.info(
                "[LOGIN] Click fallbacks did not change auth view. Navigating directly to trusted href: {}",
                resolvedUrl
        );

        try {
            page.navigate(resolvedUrl);
            page.waitForTimeout(AUTH_SWITCH_RETRY_DELAY_MS);
        } catch (Exception exception) {
            log.warn(
                    "[LOGIN] Direct trusted href navigation failed: {}",
                    friendlyMessage(exception)
            );
        }

        if (hasAuthenticationViewChanged(
                page,
                registerView,
                loginView,
                switchToLogin,
                emailLogin,
                emailInput
        )) {
            log.info(
                    "[LOGIN] Authentication view changed after direct navigation."
            );
            return true;
        }

        return false;
    }

    private boolean hasAuthenticationViewChanged(
            Page page,
            Locator registerView,
            Locator loginView,
            Locator switchToLogin,
            Locator emailLogin,
            Locator emailInput
    ) {
        return isVisible(emailInput)
                || isLoginSelectionVisible(loginView, emailLogin)
                || !isRegistrationSelectionVisible(
                        page,
                        registerView,
                        switchToLogin
                );
    }

    private boolean isLoginSelectionVisible(
            Locator loginView,
            Locator emailLogin
    ) {
        return isVisible(loginView)
                || isVisible(emailLogin);
    }

    private boolean isRegistrationSelectionVisible(
            Page page,
            Locator registerView,
            Locator switchToLogin
    ) {
        return isVisible(registerView)
                || isVisible(switchToLogin)
                || isRegisterSelectTypeUrl(page.url());
    }

    private boolean isRegisterSelectTypeUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }

        try {
            URI uri = URI.create(url);
            String path = uri.getPath();

            return MarketplaceUrls.isVintedUrl(url)
                    && path != null
                    && (REGISTER_SELECT_TYPE_PATH.equals(path)
                    || (REGISTER_SELECT_TYPE_PATH + "/").equals(path));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private boolean waitForVisible(
            Locator locator,
            double timeoutMs
    ) {
        try {
            locator.waitFor(
                    new Locator.WaitForOptions()
                            .setState(WaitForSelectorState.VISIBLE)
                            .setTimeout(timeoutMs)
            );
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    private boolean isVisible(Locator locator) {
        try {
            return locator.isVisible();
        } catch (Exception exception) {
            return false;
        }
    }

    private String safeAttribute(
            Locator locator,
            String attributeName
    ) {
        try {
            return locator.getAttribute(attributeName);
        } catch (Exception exception) {
            log.debug(
                    "[LOGIN] Could not read authentication element attribute '{}'.",
                    attributeName
            );
            return null;
        }
    }

    private boolean isNavigableHref(String href) {
        if (href == null || href.isBlank()) {
            return false;
        }

        String normalized = href.trim().toLowerCase();

        return !normalized.startsWith("#")
                && !normalized.startsWith("javascript:");
    }

    private String resolveUrl(
            String currentUrl,
            String href
    ) {
        try {
            return URI.create(currentUrl)
                    .resolve(href)
                    .toString();
        } catch (Exception exception) {
            log.warn(
                    "[LOGIN] Could not resolve href {} against current URL {}",
                    href,
                    currentUrl
            );
            return href;
        }
    }

    private void fillCredentials(Page page) {
        Locator emailInput =
                page.locator("#" + LoginSelectors.EMAIL_INPUT);

        emailInput.waitFor(
                new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(10_000)
        );

        emailInput.fill(context.getBot().getEmail());

        Locator passwordInput =
                page.locator("#" + LoginSelectors.PASSWORD_INPUT);

        passwordInput.waitFor(
                new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(10_000)
        );

        passwordInput.fill(context.getBot().getPassword());
    }

    private void submitLogin(Page page) {
        Locator submitButton =
                page.getByRole(
                        AriaRole.BUTTON,
                        new Page.GetByRoleOptions()
                                .setName(LoginSelectors.SUBMIT_BUTTON)
                );

        submitButton.waitFor(
                new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(10_000)
        );

        log.info("[LOGIN] Submitting login form.");
        submitButton.click();
    }

    private void logVisibleTestIds(Page page) {
        Locator elements = page.locator("[data-testid]");
        int count = elements.count();

        log.debug("[LOGIN DIAGNOSTIC] Visible Vinted data-testid elements:");

        for (int i = 0; i < count; i++) {
            Locator element = elements.nth(i);

            try {
                if (!element.isVisible()) {
                    continue;
                }

                String testId =
                        element.getAttribute("data-testid");
                String text = element.innerText();

                if (text != null) {
                    text = text.replaceAll("\\s+", " ").trim();

                    if (text.length() > 150) {
                        text = text.substring(0, 150);
                    }
                }

                log.debug(
                        "[LOGIN DIAGNOSTIC] testId={} text={}",
                        testId,
                        text
                );
            } catch (Exception ignored) {
            }
        }
    }

    private String friendlyMessage(Throwable exception) {
        if (exception == null
                || exception.getMessage() == null
                || exception.getMessage().isBlank()) {
            return exception == null
                    ? "unknown error"
                    : exception.getClass().getSimpleName();
        }

        return exception.getMessage()
                .lines()
                .findFirst()
                .orElse(exception.getMessage())
                .trim();
    }
}
