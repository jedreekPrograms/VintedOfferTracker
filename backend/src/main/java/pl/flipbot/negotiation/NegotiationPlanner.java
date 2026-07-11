package pl.flipbot.negotiation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.flipbot.bot.Bot;
import pl.flipbot.listing.Listing;
import pl.flipbot.listing.ListingRepository;
import pl.flipbot.listing.ListingStatus;

import java.util.List;

@Component
@RequiredArgsConstructor
public class NegotiationPlanner {

    private final ListingRepository listingRepository;

    public int calculateNewNegotiations(Bot bot) {

        List<Listing> activeListings =
                listingRepository.findByBotIdAndStatus(
                        bot.getId(),
                        ListingStatus.NEGOTIATING
                );

        int maxSteps =
                bot.getConfiguration()
                        .getNegotiationSteps()
                        .size();

        int budget =
                bot.getConfiguration()
                        .getDailyNegotiationBudget();

        int usedBudget = 0;

        for (Listing listing : activeListings) {

            usedBudget +=
                    maxSteps - listing.getCurrentStep() + 1;

        }

        int remainingBudget =
                budget - usedBudget;

        if (remainingBudget <= 0) {
            return 0;
        }

        return remainingBudget / maxSteps;

    }

}