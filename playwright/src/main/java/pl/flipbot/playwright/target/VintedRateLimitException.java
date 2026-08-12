package pl.flipbot.playwright.target;

public class VintedRateLimitException
        extends RuntimeException {

    public VintedRateLimitException(
            String message
    ) {

        super(
                message
        );
    }
}