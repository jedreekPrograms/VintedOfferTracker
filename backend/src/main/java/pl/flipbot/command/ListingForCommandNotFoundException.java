package pl.flipbot.command;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class ListingForCommandNotFoundException
        extends RuntimeException {

    public ListingForCommandNotFoundException(
            Long botId,
            Long listingId
    ) {
        super(
                "Listing "
                        + listingId
                        + " was not found for bot "
                        + botId
        );
    }
}