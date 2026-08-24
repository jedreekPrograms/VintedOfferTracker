package pl.flipbot.negotiation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pl.flipbot.bot.Bot;
import pl.flipbot.bot.configuration.BotConfiguration;
import pl.flipbot.listing.Listing;
import pl.flipbot.listing.ListingRepository;
import pl.flipbot.listing.ListingStatus;
import pl.flipbot.negotiation.snapshot.NegotiationStrategySnapshotService;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class NegotiationPlanner {

    private final ListingRepository listingRepository;
    private final NegotiationStrategySnapshotService snapshotService;

    /**
     * Calculates how many NEW conversations may be started without spending
     * action slots already needed by active conversations.
     *
     * New conversations reserve the CURRENT live ladder size. Active
     * conversations reserve the ladder size frozen in their per-listing
     * strategy snapshot. This distinction is critical when the user changes,
     * for example, a five-step future strategy to three steps while old
     * five-step conversations are still running.
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

        int stepsRequiredPerNewConversation =
                configuration.getNegotiationSteps().size();
        if (stepsRequiredPerNewConversation <= 0 || remainingDailyActions <= 0) {
            return 0;
        }

        int reservedFutureSteps = calculateReservedFutureSteps(
                bot.getId(),
                stepsRequiredPerNewConversation
        );

        int actionsAvailableForNewConversations =
                remainingDailyActions - reservedFutureSteps;

        int allowedNewNegotiations =
                actionsAvailableForNewConversations < stepsRequiredPerNewConversation
                        ? 0
                        : actionsAvailableForNewConversations
                        / stepsRequiredPerNewConversation;

        log.info(
                "[NEGOTIATION CAPACITY] Bot {}: remainingDailyActions={}, reservedFutureSteps={}, stepsRequiredPerNewConversation={}, actionsLeftAfterReservations={}, allowedNewNegotiations={}.",
                bot.getId(),
                remainingDailyActions,
                reservedFutureSteps,
                stepsRequiredPerNewConversation,
                Math.max(actionsAvailableForNewConversations, 0),
                allowedNewNegotiations
        );

        return allowedNewNegotiations;
    }

    int calculateReservedFutureSteps(
            Long botId,
            int liveFallbackStepCount
    ) {
        if (botId == null || liveFallbackStepCount <= 0) {
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
            int frozenStepCount = snapshotService.stepCountForActiveListing(
                    listing,
                    liveFallbackStepCount
            );

            /*
             * currentStep is the last already-sent step. Invalid state fails
             * safe by reserving the whole frozen ladder.
             */
            int remainingSteps;
            if (currentStep == null || currentStep < 1) {
                remainingSteps = frozenStepCount;
            } else {
                remainingSteps = Math.max(
                        frozenStepCount - currentStep,
                        0
                );
            }

            reservedFutureSteps += remainingSteps;

            log.info(
                    "[NEGOTIATION CAPACITY] Bot {} active listing backendId={}, marketplaceId={}, status={}, currentStep={}, frozenStepCount={} reserves {} future action(s).",
                    botId,
                    listing.getId(),
                    listing.getListingId(),
                    listing.getStatus(),
                    currentStep,
                    frozenStepCount,
                    remainingSteps
            );
        }

        log.info(
                "[NEGOTIATION CAPACITY] Bot {} has {} active negotiation(s) reserving {} future action(s) in total.",
                botId,
                activeListings.size(),
                reservedFutureSteps
        );

        return reservedFutureSteps;
    }
}
