package pl.flipbot.playwright.verification;

/**
 * Marks the current recovery thread as belonging to a bot whose headed CAPTCHA
 * browser may be controlled from FlipBot Mobile. LoginService creates its own
 * HumanVerificationHandler, so a small thread-local scope keeps the bot ID
 * available without coupling the login package to mobile transport details.
 */
final class MobileCaptchaControlScope implements AutoCloseable {

    private static final ThreadLocal<Long> CURRENT_BOT_ID = new ThreadLocal<>();

    private final Long previousBotId;

    private MobileCaptchaControlScope(Long botId) {
        this.previousBotId = CURRENT_BOT_ID.get();
        CURRENT_BOT_ID.set(botId);
    }

    static MobileCaptchaControlScope open(Long botId) {
        if (botId == null || botId <= 0L) {
            throw new IllegalArgumentException(
                    "Mobile CAPTCHA control scope requires a positive bot ID"
            );
        }
        return new MobileCaptchaControlScope(botId);
    }

    static Long currentBotId() {
        return CURRENT_BOT_ID.get();
    }

    @Override
    public void close() {
        if (previousBotId == null) {
            CURRENT_BOT_ID.remove();
        } else {
            CURRENT_BOT_ID.set(previousBotId);
        }
    }
}
