package pl.flipbot.command;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class BotCommandCannotBeCreatedException
        extends RuntimeException {

    public BotCommandCannotBeCreatedException(
            String message
    ) {
        super(message);
    }
}