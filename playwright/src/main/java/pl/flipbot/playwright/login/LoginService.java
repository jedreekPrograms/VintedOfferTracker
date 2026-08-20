package pl.flipbot.playwright.login;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.marketplace.MarketplaceNavigator;
import pl.flipbot.playwright.marketplace.MarketplaceUrls;
import pl.flipbot.playwright.verification.HumanVerificationHandler;

import java.net.URI;
import java.util.List;
import java.util.Locale;

@Slf4j
@RequiredArgsConstructor
public class LoginService {

    private static final double AUTH_VIEW_TIMEOUT_MS = 20_000;
    private static final double AUTH_POLL_INTERVAL_MS = 200;
    private static final double AUTH_SWITCH_RETRY_DELAY_MS = 600;
    private static final double FORM_SETTLE_MS = 700;
    private static final double CREDENTIAL_STABILITY_WAIT_MS = 650;
    private static final double SUBMIT_TRANSITION_TIMEOUT_MS = 6_000;
    private static final double POST_LOGIN_TIMEOUT_MS = 60_000;

    private static final int POST_LOGIN_STABLE_FALLBACK_POLLS = 4;
    private static final int MAX_REGISTER_SWITCH_ATTEMPTS = 6;
    private static final int MAX_CREDENTIAL_FILL_ATTEMPTS = 4;
    private static final int MAX_SUBMIT_ATTEMPTS = 3;

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

    private static final List<String> LOGIN_SUBMIT_LABELS =
            List.of(
                    "zaloguj się",
                    "zaloguj sie",
                    "log in",
                    "login",
                    "kontynuuj",
                    "continue"
            );

    private static final List<String> EXPLICIT_LOGIN_ERROR_TEXTS =
            List.of(
                    "nieprawidłowy e-mail lub hasło",
                    "nieprawidlowy e-mail lub haslo",
                    "nieprawidłowy adres e-mail lub hasło",
                    "nieprawidlowy adres e-mail lub haslo",
                    "niepoprawne hasło",
                    "niepoprawne haslo",
                    "incorrect email or password",
                    "invalid email or password",
                    "wrong password",
                    "too many attempts",
                    "zbyt wiele prób",
                    "zbyt wiele prob"
            );

    private final BotContext context;
    private final HumanVerificationHandler humanVerificationHandler =
            new HumanVerificationHandler();

    public void login() {
        Page page = context.getPage();

        hideAutomation(page);

        log.info(
                "[LOGIN] Opening Vinted homepage for bot {}.",
                context.getBot().getId()
        );

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

        performLogin(page);
    }

    private void hideAutomation(Page page) {
        page.context().addInitScript(
                "delete Object.getPrototypeOf(navigator).webdriver;"
        );
    }

    private void acceptCookiesIfVisible(Page page) {
        try {
            Locator button = page.locator("#onetrust-accept-btn-handler");

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

    private void performLogin(Page page) {
        log.info(
                "[LOGIN] Starting interactive login for bot {}.",
                context.getBot().getId()
        );

        validateConfiguredCredentials();
        openLoginWindow(page);
        openEmailLogin(page);

        LoginForm form = resolveLoginForm(page);
        stabilizeCredentials(page, form);
        submitLoginDeterministically(page, form);

        String authenticatedBy = waitForAuthenticatedSession(page);

        context.saveSession();

        log.info(
                "[LOGIN] Bot {} logged in successfully. verifiedBy={}",
                context.getBot().getId(),
                authenticatedBy
        );
    }

    private void validateConfiguredCredentials() {
        if (context.getBot().getEmail() == null
                || context.getBot().getEmail().isBlank()) {
            throw new IllegalStateException(
                    "Bot has no Vinted e-mail configured"
            );
        }

        if (context.getBot().getPassword() == null
                || context.getBot().getPassword().isBlank()) {
            throw new IllegalStateException(
                    "Bot has no Vinted password configured"
            );
        }
    }

    private void openLoginWindow(Page page) {
        Locator loginButton = page.getByTestId(LoginSelectors.LOGIN_BUTTON);

        loginButton.waitFor(
                new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(10_000)
        );

        log.info("[LOGIN] Opening authentication window.");
        loginButton.click();
    }

    private void openEmailLogin(Page page) {
        Locator emailInput = page.locator("#" + LoginSelectors.EMAIL_INPUT);
        Locator registerView = page.getByTestId(REGISTER_VIEW_TEST_ID);
        Locator loginView = page.getByTestId(LOGIN_VIEW_TEST_ID);
        Locator switchToLogin = page.getByTestId(REGISTER_SWITCH_TEST_ID);
        Locator emailLogin = page.getByTestId(LOGIN_EMAIL_TEST_ID);

        long deadline =
                System.currentTimeMillis() + (long) AUTH_VIEW_TIMEOUT_MS;

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

                if (!waitForVisible(switchToLogin, 5_000)) {
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

        logLoginDiagnostics(page);

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

    private LoginForm resolveLoginForm(Page page) {
        Locator emailInput = page.locator("#" + LoginSelectors.EMAIL_INPUT);
        Locator passwordInput = page.locator("#" + LoginSelectors.PASSWORD_INPUT);

        emailInput.waitFor(
                new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(10_000)
        );
        passwordInput.waitFor(
                new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(10_000)
        );

        /*
         * The log that exposed the bug showed the form becoming visible and
         * the old code trying to submit only ~20 ms later. Give Vinted's React
         * form time to finish hydrating before writing controlled inputs.
         */
        page.waitForTimeout(FORM_SETTLE_MS);

        Locator form = passwordInput.locator("xpath=ancestor::form[1]");
        if (form.count() == 0) {
            form = emailInput.locator("xpath=ancestor::form[1]");
        }

        if (form.count() == 0) {
            throw new IllegalStateException(
                    "Vinted e-mail/password inputs are visible, but no containing login form could be resolved."
            );
        }

        return new LoginForm(
                emailInput,
                passwordInput,
                form.first()
        );
    }

    private void stabilizeCredentials(
            Page page,
            LoginForm form
    ) {
        String expectedEmail = context.getBot().getEmail();
        String expectedPassword = context.getBot().getPassword();

        for (int attempt = 1;
             attempt <= MAX_CREDENTIAL_FILL_ATTEMPTS;
             attempt++) {
            form.emailInput().fill(expectedEmail);
            form.passwordInput().fill(expectedPassword);

            page.waitForTimeout(CREDENTIAL_STABILITY_WAIT_MS);

            boolean emailStable = inputValueEquals(
                    form.emailInput(),
                    expectedEmail
            );
            boolean passwordStable = inputValueEquals(
                    form.passwordInput(),
                    expectedPassword
            );

            if (emailStable && passwordStable) {
                log.info(
                        "[LOGIN] Credentials are stable in the Vinted form after fill attempt {}/{}. Values are not logged.",
                        attempt,
                        MAX_CREDENTIAL_FILL_ATTEMPTS
                );
                return;
            }

            log.warn(
                    "[LOGIN] Vinted cleared or rewrote credential field(s) after fill attempt {}/{}. emailStable={}, passwordStable={}. Re-filling only after the form has settled.",
                    attempt,
                    MAX_CREDENTIAL_FILL_ATTEMPTS,
                    emailStable,
                    passwordStable
            );

            page.waitForTimeout(FORM_SETTLE_MS);
        }

        logLoginDiagnostics(page);
        throw new IllegalStateException(
                "Vinted login form did not retain configured credentials long enough to submit safely."
        );
    }

    private void submitLoginDeterministically(
            Page page,
            LoginForm form
    ) {
        Locator submitButton = resolveSubmitButton(form.form());

        if (submitButton == null) {
            logLoginDiagnostics(page);
            throw new IllegalStateException(
                    "Could not find a visible login submit control inside the Vinted e-mail/password form."
            );
        }

        String submitDescription = describeSubmitControl(submitButton);
        String initialUrl = page.url();

        for (int attempt = 1; attempt <= MAX_SUBMIT_ATTEMPTS; attempt++) {
            if (!credentialsStillMatch(form)) {
                log.warn(
                        "[LOGIN] Credential fields changed before submit attempt {}/{}. Stabilizing them again before any submit action.",
                        attempt,
                        MAX_SUBMIT_ATTEMPTS
                );
                stabilizeCredentials(page, form);
            }

            String explicitErrorBeforeSubmit = detectExplicitLoginError(page);
            if (explicitErrorBeforeSubmit != null) {
                throw new IllegalStateException(
                        "Vinted login form exposes an explicit authentication error before retry: "
                                + explicitErrorBeforeSubmit
                );
            }

            if (attempt == 1) {
                log.info(
                        "[LOGIN] Submit attempt 1/{}: clicking the visible submit control INSIDE the credential form. control={}",
                        MAX_SUBMIT_ATTEMPTS,
                        submitDescription
                );

                submitButton.scrollIntoViewIfNeeded();

                if (!submitButton.isEnabled()) {
                    throw new IllegalStateException(
                            "Vinted login submit control is visible but disabled while both credential fields are stable. control="
                                    + submitDescription
                    );
                }

                submitButton.click();
            } else if (attempt == 2) {
                log.warn(
                        "[LOGIN] Previous click produced no observable login transition. Submit attempt 2/{}: pressing Enter on the stable password field.",
                        MAX_SUBMIT_ATTEMPTS
                );
                form.passwordInput().press("Enter");
            } else {
                log.warn(
                        "[LOGIN] Click and Enter produced no observable login transition. Submit attempt 3/{}: invoking HTMLFormElement.requestSubmit() on the exact credential form so normal submit handlers and validation still run.",
                        MAX_SUBMIT_ATTEMPTS
                );
                form.form().evaluate("form => form.requestSubmit()");
            }

            String transition = waitForSubmissionTransition(
                    page,
                    form,
                    initialUrl
            );

            if (transition != null) {
                log.info(
                        "[LOGIN] Login submit attempt {}/{} produced an observable transition: {}. Current URL: {}",
                        attempt,
                        MAX_SUBMIT_ATTEMPTS,
                        transition,
                        page.url()
                );
                return;
            }

            String explicitError = detectExplicitLoginError(page);
            if (explicitError != null) {
                log.error(
                        "[LOGIN] Vinted returned an explicit login error after submit attempt {}/{}: {}",
                        attempt,
                        MAX_SUBMIT_ATTEMPTS,
                        explicitError
                );
                logLoginDiagnostics(page);
                throw new IllegalStateException(
                        "Vinted rejected the login form: " + explicitError
                );
            }

            log.warn(
                    "[LOGIN] Submit attempt {}/{} completed at Playwright level, but Vinted showed no authenticated state, no real human-verification evidence, no auth URL transition and no explicit login error within {}s.",
                    attempt,
                    MAX_SUBMIT_ATTEMPTS,
                    Math.round(SUBMIT_TRANSITION_TIMEOUT_MS / 1_000)
            );

            if (attempt < MAX_SUBMIT_ATTEMPTS) {
                page.waitForTimeout(FORM_SETTLE_MS);
                submitButton = resolveSubmitButton(form.form());
                if (submitButton == null && attempt < 2) {
                    log.warn(
                            "[LOGIN] Submit button disappeared, but credential form remains. Keyboard/requestSubmit fallbacks can still be used."
                    );
                }
            }
        }

        logLoginDiagnostics(page);
        throw new IllegalStateException(
                "Vinted login form accepted three submit mechanisms without producing any observable authentication transition. Refusing to pretend that login or human verification happened. Current URL: "
                        + page.url()
        );
    }

    private String waitForSubmissionTransition(
            Page page,
            LoginForm form,
            String initialUrl
    ) {
        long deadline =
                System.currentTimeMillis()
                        + (long) SUBMIT_TRANSITION_TIMEOUT_MS;

        while (System.currentTimeMillis() <= deadline) {
            String authenticated = authenticatedSignal(page, false);
            if (authenticated != null) {
                return "authenticated by " + authenticated;
            }

            if (humanVerificationHandler.isHumanVerificationVisible(page)) {
                log.warn(
                        "[LOGIN] REAL human-verification evidence detected after submit. Pausing only because the challenge is actually visible in the page/iframe."
                );

                humanVerificationHandler.waitUntilVerified(page);

                String afterVerification = authenticatedSignal(page, false);
                if (afterVerification != null) {
                    return "human verification completed; authenticated by "
                            + afterVerification;
                }

                return "human verification was positively detected and completed";
            }

            String currentUrl = page.url();
            if (!sameUrl(initialUrl, currentUrl)) {
                return "URL changed from the submitted login page";
            }

            if (!isVisible(form.emailInput())
                    || !isVisible(form.passwordInput())) {
                return "credential form disappeared";
            }

            page.waitForTimeout(AUTH_POLL_INTERVAL_MS);
        }

        return null;
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

            if (humanVerificationHandler.isHumanVerificationVisible(page)) {
                log.warn(
                        "[LOGIN] Human verification is visibly present while waiting for authenticated session."
                );
                humanVerificationHandler.waitUntilVerified(page);
                stableFallbackPolls = 0;
                continue;
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

            String explicitError = detectExplicitLoginError(page);
            if (explicitError != null) {
                logLoginDiagnostics(page);
                throw new IllegalStateException(
                        "Vinted rejected the login form: " + explicitError
                );
            }

            page.waitForTimeout(AUTH_POLL_INTERVAL_MS);
        }

        log.error(
                "[LOGIN] Login submission could not be verified within {}s for bot {}. Current URL: {}. The session will NOT be saved as authenticated.",
                Math.round(POST_LOGIN_TIMEOUT_MS / 1_000),
                context.getBot().getId(),
                page.url()
        );
        logLoginDiagnostics(page);

        throw new IllegalStateException(
                "Vinted login submission could not be verified. Current URL: "
                        + page.url()
        );
    }

    private Locator resolveSubmitButton(Locator form) {
        Locator typedSubmit = form.locator(
                "button[type='submit']:visible, input[type='submit']:visible"
        );

        int typedCount = safeCount(typedSubmit);
        for (int index = 0; index < typedCount; index++) {
            Locator candidate = typedSubmit.nth(index);
            if (isVisible(candidate)) {
                return candidate;
            }
        }

        Locator buttons = form.locator("button:visible");
        int buttonCount = Math.min(safeCount(buttons), 20);

        for (int index = 0; index < buttonCount; index++) {
            Locator candidate = buttons.nth(index);
            if (!isVisible(candidate)) {
                continue;
            }

            String text = safeInnerText(candidate);
            if (isLoginSubmitLabel(text)) {
                return candidate;
            }
        }

        return null;
    }

    static boolean isLoginSubmitLabel(String text) {
        if (text == null) {
            return false;
        }

        String normalized = text
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);

        return LOGIN_SUBMIT_LABELS.contains(normalized);
    }

    private String describeSubmitControl(Locator submitButton) {
        String type = safeAttribute(submitButton, "type");
        String text = safeInnerText(submitButton)
                .replaceAll("\\s+", " ")
                .trim();

        if (text.length() > 80) {
            text = text.substring(0, 80);
        }

        return "type='" + type + "', text='" + text + "'";
    }

    private boolean credentialsStillMatch(LoginForm form) {
        return inputValueEquals(
                form.emailInput(),
                context.getBot().getEmail()
        ) && inputValueEquals(
                form.passwordInput(),
                context.getBot().getPassword()
        );
    }

    private boolean inputValueEquals(
            Locator input,
            String expected
    ) {
        try {
            return expected != null
                    && expected.equals(input.inputValue());
        } catch (PlaywrightException exception) {
            return false;
        }
    }

    private String detectExplicitLoginError(Page page) {
        try {
            String bodyText = page.locator("body").innerText();
            if (bodyText == null || bodyText.isBlank()) {
                return null;
            }

            String normalized = bodyText.toLowerCase(Locale.ROOT);
            for (String phrase : EXPLICIT_LOGIN_ERROR_TEXTS) {
                if (normalized.contains(phrase)) {
                    return phrase;
                }
            }
        } catch (PlaywrightException exception) {
            log.debug(
                    "[LOGIN] Login-error probe was inconclusive while the page was changing. This is NOT treated as verification or authentication failure by itself."
            );
        }

        return null;
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

        Locator visibleInboxLinks = page.locator("a[href*='/inbox']:visible");

        if (safeCount(visibleInboxLinks) > 0) {
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
        return isVisible(loginView) || isVisible(emailLogin);
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

            String normalizedPath = path.toLowerCase(Locale.ROOT);
            return normalizedPath.startsWith("/member/register")
                    || normalizedPath.startsWith("/member/login")
                    || normalizedPath.startsWith("/member/auth");
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
            return locator != null && locator.isVisible();
        } catch (Exception exception) {
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

    private int safeCount(Locator locator) {
        try {
            return locator == null ? 0 : locator.count();
        } catch (RuntimeException exception) {
            return 0;
        }
    }

    private String safeAttribute(
            Locator locator,
            String attributeName
    ) {
        try {
            String value = locator.getAttribute(attributeName);
            return value == null ? "" : value;
        } catch (Exception exception) {
            return "";
        }
    }

    private String safeInnerText(Locator locator) {
        try {
            String text = locator.innerText();
            return text == null ? "" : text;
        } catch (Exception exception) {
            return "";
        }
    }

    private boolean isNavigableHref(String href) {
        if (href == null || href.isBlank()) {
            return false;
        }

        String normalized = href.trim().toLowerCase(Locale.ROOT);

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

    private boolean sameUrl(String first, String second) {
        if (first == null) {
            return second == null;
        }
        return first.equals(second);
    }

    private void logLoginDiagnostics(Page page) {
        Locator emailInput = page.locator("#" + LoginSelectors.EMAIL_INPUT);
        Locator passwordInput = page.locator("#" + LoginSelectors.PASSWORD_INPUT);

        log.debug(
                "[LOGIN DIAGNOSTIC] currentUrl={}, emailVisible={}, passwordVisible={}, emailHasValue={}, passwordHasValue={}",
                page.url(),
                isVisible(emailInput),
                isVisible(passwordInput),
                hasAnyInputValue(emailInput),
                hasAnyInputValue(passwordInput)
        );

        Locator buttons = page.locator("button:visible");
        int buttonCount = Math.min(safeCount(buttons), 30);

        for (int index = 0; index < buttonCount; index++) {
            Locator button = buttons.nth(index);
            String text = safeInnerText(button)
                    .replaceAll("\\s+", " ")
                    .trim();

            if (text.length() > 100) {
                text = text.substring(0, 100);
            }

            log.debug(
                    "[LOGIN DIAGNOSTIC] visibleButton index={} type={} text={}",
                    index,
                    safeAttribute(button, "type"),
                    text
            );
        }

        logVisibleTestIds(page);
    }

    private boolean hasAnyInputValue(Locator input) {
        try {
            return input.count() > 0
                    && !input.first().inputValue().isBlank();
        } catch (Exception exception) {
            return false;
        }
    }

    private void logVisibleTestIds(Page page) {
        Locator elements = page.locator("[data-testid]");
        int count = Math.min(safeCount(elements), 100);

        log.debug("[LOGIN DIAGNOSTIC] Visible Vinted data-testid elements:");

        for (int index = 0; index < count; index++) {
            Locator element = elements.nth(index);

            try {
                if (!element.isVisible()) {
                    continue;
                }

                String testId = element.getAttribute("data-testid");
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

    private record LoginForm(
            Locator emailInput,
            Locator passwordInput,
            Locator form
    ) {
    }
}
