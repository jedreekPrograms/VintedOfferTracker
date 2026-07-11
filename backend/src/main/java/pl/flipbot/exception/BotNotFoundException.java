package pl.flipbot.exception;

public class BotNotFoundException extends RuntimeException {

    public BotNotFoundException(Long botId) {
        super("Bot with id " + botId + " was not found.");
    }
}
