package pl.flipbot.playwright.login;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.context.BotContext;

@Slf4j
@RequiredArgsConstructor
public class LoginService {

    private static final String HOME_URL =
            "https://vinted.pl";

    private static final double AUTH_VIEW_TIMEOUT_MS =
            10_000;

    private static final double AUTH_POLL_INTERVAL_MS =
            200;

    private final BotContext context;

    public void login() {

        Page page =
                context.getPage();

        hideAutomation(
                page
        );

        log.info(
                "Opening Vinted homepage..."
        );

        page.navigate(
                HOME_URL
        );

        page.waitForLoadState();

        acceptCookiesIfVisible(
                page
        );

        if (isLoggedIn()) {

            log.info(
                    "Bot {} is already logged in.",
                    context.getBot().getId()
            );

            return;
        }

        performLogin();
    }

    private void hideAutomation(
            Page page
    ) {

        page.context()
                .addInitScript(
                        "delete Object.getPrototypeOf(navigator).webdriver;"
                );
    }

    private boolean isLoggedIn() {

        try {

            return context.getPage()
                    .getByTestId(
                            LoginSelectors.CONVERSATIONS_BUTTON
                    )
                    .isVisible();

        } catch (Exception exception) {

            return false;
        }
    }

    private void acceptCookiesIfVisible(
            Page page
    ) {

        try {

            Locator button =
                    page.locator(
                            "#onetrust-accept-btn-handler"
                    );

            button.waitFor(
                    new Locator.WaitForOptions()
                            .setState(
                                    WaitForSelectorState.VISIBLE
                            )
                            .setTimeout(
                                    5_000
                            )
            );

            log.info(
                    "Clicking cookie button..."
            );

            button.click();

            log.info(
                    "Cookies accepted."
            );

        } catch (Exception exception) {

            log.debug(
                    "Cookie banner not displayed."
            );
        }
    }

    private void performLogin() {

        Page page =
                context.getPage();

        log.info(
                "Logging in {}",
                context.getBot().getEmail()
        );

        openLoginWindow(
                page
        );

        openEmailLogin(
                page
        );

        fillCredentials(
                page
        );

        submitLogin(
                page
        );

        page.getByTestId(
                        LoginSelectors.CONVERSATIONS_BUTTON
                )
                .waitFor(
                        new Locator.WaitForOptions()
                                .setState(
                                        WaitForSelectorState.VISIBLE
                                )
                                .setTimeout(
                                        30_000
                                )
                );

        context.saveSession();

        log.info(
                "Bot {} logged in successfully.",
                context.getBot().getId()
        );
    }

    private void openLoginWindow(
            Page page
    ) {

        Locator loginButton =
                page.getByTestId(
                        LoginSelectors.LOGIN_BUTTON
                );

        loginButton.waitFor(
                new Locator.WaitForOptions()
                        .setState(
                                WaitForSelectorState.VISIBLE
                        )
                        .setTimeout(
                                10_000
                        )
        );

        log.info(
                "Opening authentication window..."
        );

        loginButton.click();
    }

    private void openEmailLogin(
            Page page
    ) {

        Locator emailInput =
                page.locator(
                        "#"
                                + LoginSelectors.EMAIL_INPUT
                );

        Locator registerView =
                page.getByTestId(
                        "select-type-register-view"
                );

        Locator loginView =
                page.getByTestId(
                        "select-type-login-view"
                );

        Locator switchToLogin =
                page.getByTestId(
                        "auth-select-type--register-switch"
                );

        Locator emailLogin =
                page.getByTestId(
                        "auth-select-type--login-email"
                );

        long deadline =
                System.currentTimeMillis()
                        + (long) AUTH_VIEW_TIMEOUT_MS;

        while (System.currentTimeMillis() < deadline) {

            /*
             * Wariant 1:
             *
             * Vinted od razu wyświetlił właściwy formularz
             * e-mail + hasło.
             */
            if (emailInput.isVisible()) {

                log.info(
                        "E-mail login form is already visible."
                );

                return;
            }

            /*
             * Wariant 2:
             *
             * Najpierw pojawił się ekran rejestracji:
             *
             * "Dołącz i sprzedawaj..."
             *
             * Musimy kliknąć:
             *
             * "Masz już konto? Zaloguj się"
             */
            if (registerView.isVisible()) {

                log.info(
                        "Registration view detected. "
                                + "Switching to login view."
                );

                switchToLogin.waitFor(
                        new Locator.WaitForOptions()
                                .setState(
                                        WaitForSelectorState.VISIBLE
                                )
                                .setTimeout(
                                        5_000
                                )
                );

                switchToLogin.click();

                break;
            }

            /*
             * Wariant 3:
             *
             * Vinted od razu pokazał:
             *
             * "Witaj ponownie!"
             *
             * więc nie musimy klikać pierwszego
             * "Zaloguj się".
             */
            if (loginView.isVisible()) {

                log.info(
                        "Login view detected directly."
                );

                break;
            }

            page.waitForTimeout(
                    AUTH_POLL_INTERVAL_MS
            );
        }

        /*
         * Po ewentualnym przełączeniu z rejestracji
         * czekamy na ekran:
         *
         * "Witaj ponownie!"
         */
        loginView.waitFor(
                new Locator.WaitForOptions()
                        .setState(
                                WaitForSelectorState.VISIBLE
                        )
                        .setTimeout(
                                AUTH_VIEW_TIMEOUT_MS
                        )
        );

        log.info(
                "Login view is visible. "
                        + "Selecting e-mail login."
        );

        /*
         * Klikamy:
         *
         * "Lub zaloguj się przez e-mail"
         */
        emailLogin.waitFor(
                new Locator.WaitForOptions()
                        .setState(
                                WaitForSelectorState.VISIBLE
                        )
                        .setTimeout(
                                AUTH_VIEW_TIMEOUT_MS
                        )
        );

        emailLogin.click();

        /*
         * Nie idziemy dalej, dopóki naprawdę
         * nie pojawi się pole e-mail.
         */
        emailInput.waitFor(
                new Locator.WaitForOptions()
                        .setState(
                                WaitForSelectorState.VISIBLE
                        )
                        .setTimeout(
                                AUTH_VIEW_TIMEOUT_MS
                        )
        );

        log.info(
                "E-mail login form is visible."
        );
    }

    private void fillCredentials(
            Page page
    ) {

        Locator emailInput =
                page.locator(
                        "#"
                                + LoginSelectors.EMAIL_INPUT
                );

        emailInput.waitFor(
                new Locator.WaitForOptions()
                        .setState(
                                WaitForSelectorState.VISIBLE
                        )
                        .setTimeout(
                                10_000
                        )
        );

        emailInput.fill(
                context.getBot().getEmail()
        );

        Locator passwordInput =
                page.locator(
                        "#"
                                + LoginSelectors.PASSWORD_INPUT
                );

        passwordInput.waitFor(
                new Locator.WaitForOptions()
                        .setState(
                                WaitForSelectorState.VISIBLE
                        )
                        .setTimeout(
                                10_000
                        )
        );

        passwordInput.fill(
                context.getBot().getPassword()
        );
    }

    private void submitLogin(
            Page page
    ) {

        Locator submitButton =
                page.getByRole(
                        AriaRole.BUTTON,
                        new Page.GetByRoleOptions()
                                .setName(
                                        LoginSelectors.SUBMIT_BUTTON
                                )
                );

        submitButton.waitFor(
                new Locator.WaitForOptions()
                        .setState(
                                WaitForSelectorState.VISIBLE
                        )
                        .setTimeout(
                                10_000
                        )
        );

        log.info(
                "Submitting login form..."
        );

        submitButton.click();
    }
}