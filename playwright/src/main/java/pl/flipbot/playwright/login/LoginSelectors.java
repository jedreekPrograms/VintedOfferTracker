package pl.flipbot.playwright.login;

public final class LoginSelectors {

    private LoginSelectors() {
    }

    // Odpowiada za data-testid="header--login-button" z Twojego linku HTML
    public static final String LOGIN_BUTTON =
            "header--login-button";

    public static final String EMAIL_INPUT =
            "username";

    public static final String PASSWORD_INPUT =
            "password";

    // Nazwa przycisku zatwierdzającego (wartość tekstowa na przycisku)
    public static final String SUBMIT_BUTTON =
            "Zaloguj się"; // Zmień na "Kontynuuj" jeśli przycisk w nowym modalu tak się nazywa

    public static final String CONVERSATIONS_BUTTON =
            "header-conversations-button";
}
