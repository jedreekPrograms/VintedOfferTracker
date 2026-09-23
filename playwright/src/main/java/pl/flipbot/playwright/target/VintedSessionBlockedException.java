package pl.flipbot.playwright.target;

public class VintedSessionBlockedException extends VintedRateLimitException {

    public VintedSessionBlockedException(String message) {
        super(message);
    }
}
