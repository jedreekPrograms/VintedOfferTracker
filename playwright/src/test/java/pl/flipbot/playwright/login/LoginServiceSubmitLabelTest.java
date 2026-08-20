package pl.flipbot.playwright.login;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LoginServiceSubmitLabelTest {

    @Test
    public void acceptsKnownLoginSubmitLabels() {
        assertTrue(LoginService.isLoginSubmitLabel("Zaloguj się"));
        assertTrue(LoginService.isLoginSubmitLabel("ZALOGUJ SIĘ"));
        assertTrue(LoginService.isLoginSubmitLabel("Log in"));
        assertTrue(LoginService.isLoginSubmitLabel("Login"));
        assertTrue(LoginService.isLoginSubmitLabel("Kontynuuj"));
        assertTrue(LoginService.isLoginSubmitLabel("Continue"));
        assertTrue(LoginService.isLoginSubmitLabel("  Zaloguj   się  "));
    }

    @Test
    public void rejectsUnrelatedAuthenticationButtons() {
        assertFalse(LoginService.isLoginSubmitLabel("Zarejestruj się"));
        assertFalse(LoginService.isLoginSubmitLabel("Nie pamiętasz hasła?"));
        assertFalse(LoginService.isLoginSubmitLabel("Apple"));
        assertFalse(LoginService.isLoginSubmitLabel("Google"));
        assertFalse(LoginService.isLoginSubmitLabel(""));
        assertFalse(LoginService.isLoginSubmitLabel(null));
    }
}
