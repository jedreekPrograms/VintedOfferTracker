package pl.flipbot.playwright.login;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.context.BotContext;

@Slf4j
@RequiredArgsConstructor
public class LoginService {

    private static final String HOME_URL = "https://vinted.pl";
    private static final int LOGIN_SWITCH_MAX_ATTEMPTS = 10;
    private static final int LOGIN_SWITCH_DELAY_MS = 300;

    private final BotContext context;

    public void login() {

        Page page = context.getPage();

        hideAutomation(page);

        log.info("Opening Vinted homepage...");

        page.navigate(HOME_URL);
        page.waitForLoadState();

        acceptCookiesIfVisible(page);

        if (isLoggedIn()) {

            log.info(
                    "Bot {} is already logged in.",
                    context.getBot().getId()
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

    private boolean isLoggedIn() {

        try {

            return context.getPage()
                    .getByTestId(LoginSelectors.CONVERSATIONS_BUTTON)
                    .isVisible();

        } catch (Exception e) {

            return false;

        }

    }

    private void acceptCookiesIfVisible(Page page) {

        try {

            Locator button = page.locator("#onetrust-accept-btn-handler");

            button.waitFor(
                    new Locator.WaitForOptions()
                            .setTimeout(5000)
            );

            log.info("Clicking cookie button...");

            button.click();

            page.waitForLoadState();

            log.info("Cookies accepted.");

        } catch (Exception e) {

            log.debug("Cookie banner not displayed.");

        }

    }

    private void performLogin() {

        Page page = context.getPage();

        log.info(
                "Logging in {}",
                context.getBot().getEmail()
        );

        openLoginWindow(page);

        switchToEmailLogin(page);

        fillCredentials(page);

        submitLogin(page);

        page.getByTestId(
                LoginSelectors.CONVERSATIONS_BUTTON
        ).waitFor();

        context.saveSession();

        log.info(
                "Bot {} logged in successfully.",
                context.getBot().getId()
        );

    }

    private void openLoginWindow(Page page) {

        Locator loginButton =
                page.getByTestId(LoginSelectors.LOGIN_BUTTON);

        loginButton.waitFor();
        loginButton.click();

    }

    private void switchToEmailLogin(Page page) {

        log.info("Switching to email login...");

        Locator emailInput =
                page.locator("#" + LoginSelectors.EMAIL_INPUT);

        for (int attempt = 0;
             attempt < LOGIN_SWITCH_MAX_ATTEMPTS;
             attempt++) {

            if (emailInput.isVisible()) {
                return;
            }

            page.evaluate("""
                    () => {
                        const button = document.querySelector(
                            '[data-testid="auth-select-type--login-email"]'
                        );

                        if (button) {
                            button.click();
                        }
                    }
                    """);

            page.waitForTimeout(LOGIN_SWITCH_DELAY_MS);

        }

        emailInput.waitFor();

    }

    private void fillCredentials(Page page) {

        Locator emailInput =
                page.locator("#" + LoginSelectors.EMAIL_INPUT);

        emailInput.waitFor();
        emailInput.fill(context.getBot().getEmail());

        Locator passwordInput =
                page.locator("#" + LoginSelectors.PASSWORD_INPUT);

        passwordInput.waitFor();
        passwordInput.fill(context.getBot().getPassword());

    }

    private void submitLogin(Page page) {

        page.getByRole(
                AriaRole.BUTTON,
                new Page.GetByRoleOptions()
                        .setName(LoginSelectors.SUBMIT_BUTTON)
        ).click();

    }

}