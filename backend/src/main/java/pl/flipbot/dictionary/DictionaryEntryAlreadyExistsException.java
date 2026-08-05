package pl.flipbot.dictionary;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(
        HttpStatus.CONFLICT
)
public class DictionaryEntryAlreadyExistsException extends RuntimeException {

    public DictionaryEntryAlreadyExistsException(
            String message
    ) {
        super(message);
    }
}
