package pl.flipbot.negotiation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.flipbot.bot.Bot;
import pl.flipbot.bot.configuration.BotConfiguration;
import pl.flipbot.listing.Listing;
import pl.flipbot.listing.ListingRepository;
import pl.flipbot.listing.ListingStatus;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class NegotiationPlanner {

    private final ListingRepository listingRepository;

    /**
     * Calculates how many NEW conversations may be started without spending
     * action slots already needed by active conversations.
     *
     * The daily quota counts actions actually sent today. Separately, every
     * active NEGOTIATING/ACTION_REQUIRED conversation reserves one slot for
     * each configured step that has not been sent yet. Starting a new
     * conversation therefore requires room for the whole configured ladder.
     *
     * Example: limit=25, five configured steps, three active conversations at
     * currentStep=3 at the beginning of a new day. Each conversation reserves
     * two future steps, so 6 slots are reserved. 19 slots remain and at most
     * floor(19/5)=3 new conversations may be started.
     */
    public int calculateNewNegotiations(
            Bot bot,
            int remainingDailyActions
    ) {
        BotConfiguration configuration = bot.getConfiguration();
        if (configuration == null
                || configuration.getNegotiationSteps() == null) {
            return 0;
        }

        int maxSteps = configuration.getNegotiationSteps().size();
        if (maxSteps <= 0 || remainingDailyActions <= 0) {
            return 0;
        }

        int reservedFutureSteps = calculateReservedFutureSteps(
                bot.getId(),
                maxSteps
        );

        int actionsAvailableForNewConversations =
                remainingDailyActions - reservedFutureSteps;

        if (actionsAvailableForNewConversations < maxSteps) {
            return 0;
        }

        return actionsAvailableForNewConversations / maxSteps;
    }

    int calculateReservedFutureSteps(
            Long botId,
            int maxSteps
    ) {
        if (botId == null || maxSteps <= 0) {
            return 0;
        }

        List<Listing> activeListings = new ArrayList<>(
                listingRepository.findByBotIdAndStatusOrderByIdAsc(
                        botId,
                        ListingStatus.NEGOTIATING
                )
        );
        activeListings.addAll(
                listingRepository.findByBotIdAndStatusOrderByIdAsc(
                        botId,
                        ListingStatus.ACTION_REQUIRED
                )
        );

        int reservedFutureSteps = 0;

        for (Listing listing : activeListings) {
            Integer currentStep = listing.getCurrentStep();

            /*
             * Once a listing is active, currentStep denotes the last step that
             * has already been sent. Therefore only maxSteps-currentStep future
             * actions need reservation. Missing/invalid step state fails safe
             * by reserving the entire ladder for that conversation.
             */
            int remainingSteps;
            if (currentStep == null || currentStep < 1) {
                remainingSteps = maxSteps;
            } else {
                remainingSteps = Math.max(
                        maxSteps - currentStep,
                        0
                );
            }

            reservedFutureSteps += remainingSteps;
        }

        return reservedFutureSteps;
    }
}
