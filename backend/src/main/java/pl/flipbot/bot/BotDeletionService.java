package pl.flipbot.bot;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.flipbot.exception.BotNotFoundException;
import pl.flipbot.listing.ListingRepository;
import pl.flipbot.listing.ListingStatus;

@Service
@RequiredArgsConstructor
public class BotDeletionService {

    private final BotRepository botRepository;

    private final ListingRepository listingRepository;


    @Transactional
    public void deleteBot(
            Long botId
    ) {

        Bot bot =
                botRepository.findById(
                                botId
                        )
                        .orElseThrow(
                                () ->
                                        new BotNotFoundException(
                                                botId
                                        )
                        );


        if (
                bot.getStatus()
                        != BotStatus.STOPPED
        ) {

            throw new IllegalStateException(
                    "Only a stopped bot can be deleted."
            );
        }


        ensureBotHasNoActiveListings(
                botId
        );


        botRepository.delete(
                bot
        );
    }


    private void ensureBotHasNoActiveListings(
            Long botId
    ) {

        boolean hasNegotiatingListings =
                !listingRepository
                        .findByBotIdAndStatusOrderByIdAsc(
                                botId,
                                ListingStatus.NEGOTIATING
                        )
                        .isEmpty();


        boolean hasActionRequiredListings =
                !listingRepository
                        .findByBotIdAndStatusOrderByIdAsc(
                                botId,
                                ListingStatus.ACTION_REQUIRED
                        )
                        .isEmpty();


        if (
                hasNegotiatingListings
                        || hasActionRequiredListings
        ) {

            throw new IllegalStateException(
                    "Bot cannot be deleted while it has active negotiations "
                            + "or listings requiring user action."
            );
        }
    }
}
