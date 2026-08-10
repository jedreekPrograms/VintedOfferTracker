package pl.flipbot.playwright.login;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.context.BotContext;

import java.net.URI;

@Slf4j
@RequiredArgsConstructor
public class LoginService {

    private static final String HOME_URL =
            "https://vinted.pl";

    private static final double AUTH_VIEW_TIMEOUT_MS =
            20_000;

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

            Locator conversationsButtons =
                    context.getPage()
                            .getByTestId(
                                    LoginSelectors.CONVERSATIONS_BUTTON
                            );

            return conversationsButtons.count() > 0
                    && conversationsButtons
                    .first()
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
                .first()
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

        boolean registerSwitchAttempted =
                false;

        while (
                System.currentTimeMillis()
                        < deadline
        ) {

            /*
             * Wariant 1:
             *
             * Formularz email + hasło
             * jest już otwarty.
             */
            if (isVisible(
                    emailInput
            )) {

                log.info(
                        "E-mail login form is already visible."
                );

                return;
            }

            /*
             * Wariant 2:
             *
             * Jesteśmy na ekranie logowania:
             *
             * "Witaj ponownie"
             *
             * i trzeba wybrać logowanie
             * przez e-mail.
             */
            if (isVisible(
                    loginView
            )) {

                log.info(
                        "Login view detected."
                );

                if (isVisible(
                        emailLogin
                )) {

                    log.info(
                            "Selecting e-mail login."
                    );

                    emailLogin.click();

                    emailInput.waitFor(
                            new Locator.WaitForOptions()
                                    .setState(
                                            WaitForSelectorState.VISIBLE
                                    )
                                    .setTimeout(
                                            10_000
                                    )
                    );

                    log.info(
                            "E-mail login form is visible."
                    );

                    return;
                }
            }

            /*
             * Wariant 3:
             *
             * Vinted pokazuje ekran rejestracji:
             *
             * "Dołącz i sprzedawaj..."
             *
             * Trzeba kliknąć:
             *
             * "Masz już konto? Zaloguj się"
             */
            if (
                    isVisible(
                            registerView
                    )
                            && !registerSwitchAttempted
            ) {

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

                String href =
                        switchToLogin.getAttribute(
                                "href"
                        );

                log.info(
                        "Login switch href: {}",
                        href
                );

                /*
                 * Najpierw normalny click Playwrighta.
                 */
                switchToLogin.click();

                page.waitForTimeout(
                        1_000
                );

                /*
                 * Jeżeli normalne kliknięcie
                 * faktycznie zmieniło ekran,
                 * wracamy na początek pętli.
                 */
                if (
                        !isVisible(
                                registerView
                        )
                                || isVisible(
                                loginView
                        )
                                || isVisible(
                                emailInput
                        )
                ) {

                    log.info(
                            "Authentication view changed "
                                    + "after normal click."
                    );

                    registerSwitchAttempted =
                            true;

                    continue;
                }

                /*
                 * Z naszych logów wiemy,
                 * że Vinted potrafi zignorować
                 * normalne click().
                 *
                 * Próbujemy więc natywnego
                 * element.click() w DOM.
                 */
                log.warn(
                        "Normal click did not change "
                                + "authentication view. "
                                + "Trying DOM click."
                );

                try {

                    switchToLogin.evaluate(
                            "element => element.click()"
                    );

                } catch (Exception exception) {

                    log.warn(
                            "DOM click failed: {}",
                            exception.getMessage()
                    );
                }

                page.waitForTimeout(
                        1_000
                );

                /*
                 * Sprawdzamy ponownie,
                 * czy pojawił się kolejny ekran.
                 */
                if (
                        !isVisible(
                                registerView
                        )
                                || isVisible(
                                loginView
                        )
                                || isVisible(
                                emailInput
                        )
                ) {

                    log.info(
                            "Authentication view changed "
                                    + "after DOM click."
                    );

                    registerSwitchAttempted =
                            true;

                    continue;
                }

                /*
                 * Jeżeli element ma prawdziwy href,
                 * możemy potraktować go jako fallback
                 * i przejść bezpośrednio na jego URL.
                 */
                if (isNavigableHref(
                        href
                )) {

                    String resolvedUrl =
                            resolveUrl(
                                    page.url(),
                                    href
                            );

                    log.warn(
                            "Clicks did not change auth view. "
                                    + "Navigating directly to href: {}",
                            resolvedUrl
                    );

                    page.navigate(
                            resolvedUrl
                    );

                    page.waitForTimeout(
                            1_000
                    );
                }

                registerSwitchAttempted =
                        true;

                log.info(
                        "URL after switching to login: {}",
                        page.url()
                );

                logVisibleTestIds(
                        page
                );

                continue;
            }

            /*
             * Jeżeli próbowaliśmy już przełączyć
             * ekran, ale nadal widzimy ekran
             * rejestracji, dajemy Vinted jeszcze
             * trochę czasu.
             */
            if (
                    registerSwitchAttempted
                            && isVisible(
                            registerView
                    )
            ) {

                page.waitForTimeout(
                        AUTH_POLL_INTERVAL_MS
                );

                continue;
            }

            page.waitForTimeout(
                    AUTH_POLL_INTERVAL_MS
            );
        }

        log.error(
                "Could not reach Vinted e-mail login form. "
                        + "Current URL: {}",
                page.url()
        );

        logVisibleTestIds(
                page
        );

        throw new IllegalStateException(
                "Vinted authentication flow could not "
                        + "reach the e-mail login form."
        );
    }

    private boolean isVisible(
            Locator locator
    ) {

        try {

            return locator.isVisible();

        } catch (Exception exception) {

            return false;
        }
    }

    private boolean isNavigableHref(
            String href
    ) {

        if (
                href == null
                        || href.isBlank()
        ) {

            return false;
        }

        String normalized =
                href.trim()
                        .toLowerCase();

        return !normalized.startsWith(
                "#"
        )
                && !normalized.startsWith(
                "javascript:"
        );
    }

    private String resolveUrl(
            String currentUrl,
            String href
    ) {

        try {

            return URI.create(
                            currentUrl
                    )
                    .resolve(
                            href
                    )
                    .toString();

        } catch (Exception exception) {

            log.warn(
                    "Could not resolve href {} "
                            + "against current URL {}",
                    href,
                    currentUrl
            );

            return href;
        }
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

    private void logVisibleTestIds(
            Page page
    ) {

        Locator elements =
                page.locator(
                        "[data-testid]"
                );

        int count =
                elements.count();

        log.info(
                "Visible Vinted elements:"
        );

        for (
                int i = 0;
                i < count;
                i++
        ) {

            Locator element =
                    elements.nth(
                            i
                    );

            try {

                if (!element.isVisible()) {
                    continue;
                }

                String testId =
                        element.getAttribute(
                                "data-testid"
                        );

                String text =
                        element.innerText();

                if (text != null) {

                    text =
                            text
                                    .replaceAll(
                                            "\\s+",
                                            " "
                                    )
                                    .trim();

                    if (text.length() > 150) {

                        text =
                                text.substring(
                                        0,
                                        150
                                );
                    }
                }

                log.info(
                        "VISIBLE TESTID: {} | TEXT: {}",
                        testId,
                        text
                );

            } catch (Exception ignored) {
            }
        }
    }
}