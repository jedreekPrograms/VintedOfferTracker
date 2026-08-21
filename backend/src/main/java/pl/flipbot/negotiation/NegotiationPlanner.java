package pl.flipbot.negotiation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pl.flipbot.bot.Bot;
import pl.flipbot.bot.configuration.BotConfiguration;
import pl.flipbot.listing.Listing;
import pl.flipbot.listing.ListingRepository;
import pl.flipbot.listing.ListingStatus;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class NegotiationPlanner {

    private final ListingRepository listingRepository;

    /**
     * Calculates how many NEW conversations may be started without spending
     * action slots already needed by conversations that are still automated.
     *
     * The daily quota counts actions actually sent today. Separately, every
     * NEGOTIATING conversation reserves one slot for each configured step that
     * has not been sent yet. Starting a new conversation therefore requires
     * room for the whole configured ladder.
     *
     * ACTION_REQUIRED is deliberately NOT reserved here. Once a conversation
     * reaches ACTION_REQUIRED it has been handed to the user and the scheduler
     * no longer sends automatic next steps for it. Keeping phantom future
     * reservations for such rows would unnecessarily suppress new discovery.
     *
     * Example: limit=25, five configured steps, three NEGOTIATING conversations
     * at currentStep=3 at the beginning of a new day. Each reserves two future
     * steps, so 6 slots are reserved. 19 slots remain and at most
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

        int allowedNewNegotiations =
                actionsAvailableForNewConversations < maxSteps
                        ? 0
                        : actionsAvailableForNewConversations / maxSteps;

        log.info(
                "[NEGOTIATION CAPACITY] Bot {}: remainingDailyActions={}, reservedFutureSteps={}, stepsRequiredPerNewConversation={}, actionsLeftAfterReservations={}, allowedNewNegotiations={}.",
                bot.getId(),
                remainingDailyActions,
                reservedFutureSteps,
                maxSteps,
                Math.max(actionsAvailableForNewConversations, 0),
                allowedNewNegotiations
        );

        return allowedNewNegotiations;
    }

    int calculateReservedFutureSteps(
            Long botId,
            int maxSteps
    ) {
        if (botId == null || maxSteps <= 0) {
            return 0;
        }

        List<Listing> activeListings =
                listingRepository.findByBotIdAndStatusOrderByIdAsc(
                        botId,
                        ListingStatus.NEGOTIATING
                );

        int reservedFutureSteps = 0;

        for (Listing listing : activeListings) {
            Integer currentStep = listing.getCurrentStep();

            /*
             * currentStep denotes the last step that has already been sent.
             * Therefore only maxSteps-currentStep future actions need
             * reservation. Missing/invalid step state fails safe by reserving
             * the entire ladder for that conversation.
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

            log.info(
                    "[NEGOTIATION CAPACITY] Bot {} automated listing backendId={}, marketplaceId={}, status={}, currentStep={} reserves {} future action(s).",
                    botId,
                    listing.getId(),
                    listing.getListingId(),
                    listing.getStatus(),
                    currentStep,
                    remainingSteps
            );
        }

        log.info(
                "[NEGOTIATION CAPACITY] Bot {} has {} NEGOTIATING conversation(s) reserving {} future action(s) in total. ACTION_REQUIRED rows reserve 0 automatic steps.",
                botId,
                activeListings.size(),
                reservedFutureSteps
        );

        return reservedFutureSteps;
    }
}
