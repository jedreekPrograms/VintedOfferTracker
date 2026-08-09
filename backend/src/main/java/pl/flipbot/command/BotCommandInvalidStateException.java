package pl.flipbot.command;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class BotCommandInvalidStateException
        extends RuntimeException {

    public BotCommandInvalidStateException(
            String message
    ) {
        super(message);
    }
}