package pl.flipbot.dictionary;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(
        HttpStatus.NOT_FOUND
)
public class DictionaryEntryNotFoundException extends RuntimeException {

    public DictionaryEntryNotFoundException(
            String message
    ) {

        super(
                message
        );

    }

}
