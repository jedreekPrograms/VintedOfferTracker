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

    public int calculateNewNegotiations(
            Bot bot
    ) {

        int maxSteps =
                bot.getConfiguration()
                        .getNegotiationSteps()
                        .size();

        int budget =
                bot.getConfiguration()
                        .getDailyNegotiationBudget();

        if (maxSteps <= 0 || budget <= 0) {
            return 0;
        }

        List<Listing> activeListings =
                listingRepository
                        .findByBotIdAndStatusOrderByIdAsc(
                                bot.getId(),
                                ListingStatus.NEGOTIATING
                        );

        int usedBudget = 0;

        for (Listing listing : activeListings) {

            int currentStep =
                    listing.getCurrentStep() == null
                            ? 1
                            : listing.getCurrentStep();

            int remainingSteps =
                    maxSteps
                            - currentStep
                            + 1;

            usedBudget += Math.max(
                    remainingSteps,
                    0
            );

        }

        int remainingBudget =
                budget - usedBudget;

        if (remainingBudget <= 0) {
            return 0;
        }

        return remainingBudget / maxSteps;

    }

}