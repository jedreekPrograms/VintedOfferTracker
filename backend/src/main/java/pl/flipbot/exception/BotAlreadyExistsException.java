package pl.flipbot.exception;

public class BotAlreadyExistsException extends RuntimeException {

    public BotAlreadyExistsException(String email) {
        super("Bot with email '" + email + "' already exists.");
    }
}
