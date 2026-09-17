package pl.flipbot.playwright.browser;

/** Local resource deferral, never a failed marketplace attempt. */
public final class BrowserCapacityUnavailableException extends RuntimeException {
    public BrowserCapacityUnavailableException(String message) {
        super(message);
    }
}
