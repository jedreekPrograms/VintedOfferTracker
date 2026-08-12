package pl.flipbot.dictionary;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.flipbot.bot.BotStatus;
import pl.flipbot.bot.configuration.BotConfiguration;
import pl.flipbot.listing.ListingRepository;
import pl.flipbot.listing.ListingStatus;

import java.util.Collection;

@Component
@RequiredArgsConstructor
public class DictionaryUsageGuard {

    private final ListingRepository listingRepository;


    public void ensureConfigurationsCanBeUpdated(
            Collection<BotConfiguration> configurations
    ) {

        for (
                BotConfiguration configuration
                : configurations
        ) {

            if (
                    configuration.getBot() == null
            ) {

                continue;
            }


            if (
                    configuration.getBot().getStatus()
                            != BotStatus.STOPPED
            ) {

                throw new IllegalStateException(
                        "Dictionary entry cannot be renamed because it is used by a running bot."
                );
            }


            Long botId =
                    configuration.getBot().getId();


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
                        "Dictionary entry cannot be renamed because it is used by a bot with active negotiations or listings requiring user action."
                );
            }
        }
    }


    public void ensureEntryIsNotUsed(
            Collection<BotConfiguration> configurations,
            String entryDescription
    ) {

        if (
                !configurations.isEmpty()
        ) {

            throw new IllegalStateException(
                    entryDescription
                            + " cannot be deleted because it is used by at least one bot configuration."
            );
        }
    }
}
