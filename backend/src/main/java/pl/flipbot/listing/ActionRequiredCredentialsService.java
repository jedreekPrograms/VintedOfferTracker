package pl.flipbot.listing;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pl.flipbot.bot.Bot;
import pl.flipbot.bot.dto.BotCredentialsResponse;

@Service
@RequiredArgsConstructor
public class ActionRequiredCredentialsService {

    private final ListingRepository listingRepository;

    @Transactional(readOnly = true)
    public BotCredentialsResponse getCredentials(
            Long botId,
            Long listingId
    ) {

        Listing listing =
                listingRepository
                        .findByIdAndBotId(
                                listingId,
                                botId
                        )
                        .orElseThrow(
                                () -> new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Listing "
                                                + listingId
                                                + " was not found for bot "
                                                + botId
                                )
                        );

        if (
                listing.getStatus()
                        != ListingStatus.ACTION_REQUIRED
        ) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Credentials can only be revealed "
                            + "for an ACTION_REQUIRED listing. "
                            + "Current status: "
                            + listing.getStatus()
            );
        }

        Bot bot =
                listing.getBot();

        if (
                bot.getEmail() == null
                        || bot.getEmail().isBlank()
        ) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Bot has no e-mail configured"
            );
        }

        if (
                bot.getPassword() == null
                        || bot.getPassword().isBlank()
        ) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Bot has no password configured"
            );
        }

        return new BotCredentialsResponse(
                bot.getEmail(),
                bot.getPassword()
        );
    }
}