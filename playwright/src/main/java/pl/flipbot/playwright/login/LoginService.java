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
    private final BotContext context;

    public void login() {
        Page page = context.getPage();

        // Ukrywanie flag bota przed Cloudflare na poziomie skryptu strony
        page.context().addInitScript("delete Object.getPrototypeOf(navigator).webdriver;");

        log.info("Opening Vinted homepage...");
        page.navigate(HOME_URL);
        page.waitForLoadState();

        // KROK 1: Akceptacja plików cookie
        acceptCookiesIfVisible(page);

        if (isLoggedIn()) {
            log.info("Bot {} is already logged in.", context.getBot().getId());
            return;
        }

        performLogin();
    }

    private boolean isLoggedIn() {
        try {
            Locator conversationsButton = context.getPage()
                    .getByTestId(LoginSelectors.CONVERSATIONS_BUTTON);
            return conversationsButton.isVisible();
        } catch (Exception e) {
            return false;
        }
    }

    private void acceptCookiesIfVisible(Page page) {
        try {
            Locator button = page.locator("#onetrust-accept-btn-handler");
            button.waitFor(new Locator.WaitForOptions().setTimeout(5000));
            log.info("Clicking cookie button...");
            button.click();
            page.waitForLoadState();
            log.info("Cookies accepted.");
        } catch (Exception e) {
            log.warn("Cannot accept cookies lub baner się nie pojawił.", e);
        }
    }

    private void performLogin() {
        Page page = context.getPage();
        log.info("Logging in {}", context.getBot().getEmail());

        // KROK 2: Kliknięcie "Zarejestruj się | Zaloguj się" z nagłówka za pomocą TestId
        Locator loginButton = page.getByTestId(LoginSelectors.LOGIN_BUTTON);
        loginButton.waitFor();
        loginButton.click();

        // KROK 3: Agresywne klikanie w przełącznik "e-mail" przez JS, aż pojawi się input
        log.info("Switching to email login using robust JS loop...");

        Locator emailInput = page.locator("#" + LoginSelectors.EMAIL_INPUT);
        int attempts = 0;

        while (!emailInput.isVisible() && attempts < 10) {
            try {
                // Wykonujemy kliknięcie czystym JavaScriptem bezpośrednio na elemencie z data-testid
                page.evaluate("() => { " +
                        "  const el = document.querySelector('[data-testid=\"auth-select-type--login-email\"]');" +
                        "  if (el) el.click();" +
                        "}");

                // Krótkie czekanie między próbami, aby React zdążył zareagować
                Thread.sleep(500);
            } catch (Exception e) {
                log.warn("Błąd podczas próby kliknięcia przełącznika, próba: {}", attempts);
            }
            attempts++;
        }

        // KROK 4: Uzupełnianie formularza danymi (teraz pola na 100% powinny być widoczne)
        log.info("Filling credentials...");
        emailInput.waitFor();
        emailInput.fill(context.getBot().getEmail());

        Locator passwordInput = page.locator("#" + LoginSelectors.PASSWORD_INPUT);
        passwordInput.waitFor();
        passwordInput.fill(context.getBot().getPassword());

        // KROK 5: Kliknięcie przycisku "Zaloguj się" / "Kontynuuj"
        page.getByRole(
                AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName(LoginSelectors.SUBMIT_BUTTON)
        ).click();

        // Oczekiwanie na przejście na ekran zalogowanego użytkownika
        page.getByTestId(LoginSelectors.CONVERSATIONS_BUTTON).waitFor();
        context.saveSession();
        log.info("Bot {} logged in successfully.", context.getBot().getId());
    }
}
