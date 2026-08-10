package pl.flipbot.playwright.negotiation;

import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.listing.ListingClient;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.api.quota.OfferQuotaClient;
import pl.flipbot.playwright.api.quota.dto.OfferQuotaReservationResponseDto;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.api.listing.ListingStatusUpdater;
import pl.flipbot.playwright.model.BotConfigurationDto;

import java.util.List;

@Slf4j
public class ExistingNegotiationProcessor {

    private final BotContext context;

    private final ListingClient listingClient;

    private final OfferQuotaClient offerQuotaClient;

    private final ListingStatusUpdater
            listingStatusUpdater;

    private final NegotiationConversationProcessor
            negotiationConversationProcessor;

    private final NegotiationDecisionService
            negotiationDecisionService;

    private final NextNegotiationStepExecutor
            nextNegotiationStepExecutor;

    private final boolean realNextStepsEnabled;

    private final int maxRealNextStepsPerRun;


    public ExistingNegotiationProcessor(
            BotContext context,
            ListingClient listingClient,
            OfferQuotaClient offerQuotaClient,
            ListingStatusUpdater listingStatusUpdater,
            boolean realNextStepsEnabled,
            int maxRealNextStepsPerRun
    ) {

        this.context =
                context;

        this.listingClient =
                listingClient;

        this.offerQuotaClient =
                offerQuotaClient;

        this.listingStatusUpdater =
                listingStatusUpdater;

        this.negotiationConversationProcessor =
                new NegotiationConversationProcessor(
                        context
                );

        this.negotiationDecisionService =
                new NegotiationDecisionService();

        this.nextNegotiationStepExecutor =
                new NextNegotiationStepExecutor(
                        context
                );

        this.realNextStepsEnabled =
                realNextStepsEnabled;

        this.maxRealNextStepsPerRun =
                maxRealNextStepsPerRun;
    }


    public boolean process() {

        Long botId =
                context.getBot()
                        .getId();


        List<ListingResponseDto> negotiatingListings =
                listingClient.getNegotiatingListings(
                        botId
                );


        log.info(
                "Bot {} currently has {} active negotiations",
                botId,
                negotiatingListings.size()
        );


        return inspectExistingNegotiations(
                negotiatingListings
        );
    }


    private boolean inspectExistingNegotiations(
            List<ListingResponseDto> negotiatingListings
    ) {

        if (negotiatingListings.isEmpty()) {

            log.info(
                    "[CONVERSATION] There are no active negotiations "
                            + "to inspect."
            );

            return false;
        }


        BotConfigurationDto configuration =
                context.getBot()
                        .getConfiguration();


        if (configuration == null) {

            throw new IllegalStateException(
                    "Bot configuration is missing"
            );
        }


        log.info(
                "[CONVERSATION] Starting inspection of {} "
                        + "active negotiations.",
                negotiatingListings.size()
        );


        int inspectedCount =
                0;

        int sentNextSteps =
                0;


        for (
                ListingResponseDto listing
                : negotiatingListings
        ) {

            try {

                inspectedCount++;


                log.info(
                        "[CONVERSATION] Inspecting negotiation {}/{}. "
                                + "Backend listing {}, marketplace listing {}, "
                                + "conversation {}, current step {}",
                        inspectedCount,
                        negotiatingListings.size(),
                        listing.id(),
                        listing.listingId(),
                        listing.conversationId(),
                        listing.currentStep()
                );


                NegotiationConversationSnapshot snapshot =
                        negotiationConversationProcessor
                                .inspectSnapshot(
                                        listing
                                );


                NegotiationDecision decision =
                        negotiationDecisionService.decide(
                                listing,
                                snapshot,
                                configuration
                        );


                boolean stepWasSent =
                        handleNegotiationDecision(
                                listing,
                                snapshot,
                                decision
                        );


                if (stepWasSent) {

                    sentNextSteps++;


                    log.warn(
                            "[NEXT STEP REAL] Sent next steps during "
                                    + "this run: {}/{}",
                            sentNextSteps,
                            maxRealNextStepsPerRun
                    );


                    if (
                            sentNextSteps
                                    >= maxRealNextStepsPerRun
                    ) {

                        log.warn(
                                "[NEXT STEP REAL] Real next-step limit "
                                        + "for this run has been reached."
                        );

                        break;
                    }
                }

            } catch (Exception exception) {

                log.error(
                        "[CONVERSATION] Failed to inspect backend listing {}, "
                                + "marketplace listing {}, conversation {}: {}",
                        listing.id(),
                        listing.listingId(),
                        listing.conversationId(),
                        getFriendlyErrorMessage(
                                exception
                        )
                );

                log.trace(
                        "[CONVERSATION] Full exception for backend listing {}.",
                        listing.id(),
                        exception
                );
            }
        }


        log.info(
                "[CONVERSATION] Finished inspection. "
                        + "Inspected: {}, real next steps sent: {}.",
                inspectedCount,
                sentNextSteps
        );


        return sentNextSteps > 0;
    }


    private boolean handleNegotiationDecision(
            ListingResponseDto listing,
            NegotiationConversationSnapshot snapshot,
            NegotiationDecision decision
    ) {

        return switch (decision.type()) {

            case WAIT -> {

                log.info(
                        "[DECISION] Marketplace listing {} remains "
                                + "NEGOTIATING. The bot is waiting "
                                + "for the seller. Conversation result: {}. "
                                + "Reason: {}",
                        listing.listingId(),
                        snapshot.result(),
                        decision.reason()
                );

                yield false;
            }


            case MARK_ACTION_REQUIRED -> {

                ListingResponseDto updatedListing =
                        listingStatusUpdater.markActionRequired(
                                listing,
                                decision
                        );


                log.warn(
                        "[DECISION] Marketplace listing {} was changed "
                                + "from NEGOTIATING to ACTION_REQUIRED. "
                                + "Current price: {}. "
                                + "The frontend may now display this listing "
                                + "as available for manual purchase. "
                                + "The bot did NOT click Buy now. "
                                + "Reason: {}",
                        updatedListing.listingId(),
                        updatedListing.currentPrice(),
                        decision.reason()
                );


                yield false;
            }


            case SEND_NEXT_STEP ->
                    processNextStep(
                            listing,
                            decision
                    );


            case MARK_REJECTED -> {

                ListingResponseDto updatedListing =
                        listingStatusUpdater.markRejected(
                                listing,
                                decision
                        );


                log.warn(
                        "[DECISION] Marketplace listing {} was changed "
                                + "from NEGOTIATING to REJECTED. "
                                + "Current price: {}. "
                                + "There are no more automated negotiation "
                                + "steps. Reason: {}",
                        updatedListing.listingId(),
                        updatedListing.currentPrice(),
                        decision.reason()
                );


                yield false;
            }


            case KEEP_UNKNOWN -> {

                log.warn(
                        "[DECISION] Marketplace listing {} remains "
                                + "NEGOTIATING because the conversation "
                                + "state could not be recognized. Reason: {}",
                        listing.listingId(),
                        decision.reason()
                );


                yield false;
            }
        };
    }


    private boolean processNextStep(
            ListingResponseDto listing,
            NegotiationDecision decision
    ) {

        if (decision.nextStep() == null) {

            throw new IllegalStateException(
                    "Decision SEND_NEXT_STEP contains no next step "
                            + "for backend listing "
                            + listing.id()
            );
        }


        log.warn(
                "[DECISION] Marketplace listing {} qualifies "
                        + "for the next negotiation step. "
                        + "Current step: {}, next step: {}, "
                        + "next offer price: {}, "
                        + "seller counteroffer: {}. Reason: {}",
                listing.listingId(),
                listing.currentStep(),
                decision.nextStep().getStepNumber(),
                decision.nextStep().getOfferPrice(),
                decision.sellerCounterOfferPrice(),
                decision.reason()
        );


        /*
         * DRY RUN.
         *
         * Nie rezerwujemy quota, bo niczego
         * faktycznie nie wysyłamy.
         */
        if (!realNextStepsEnabled) {

            log.warn(
                    "[NEXT STEP DRY RUN] Real next steps are disabled. "
                            + "Preparing step {} for marketplace listing {} "
                            + "without sending it.",
                    decision.nextStep().getStepNumber(),
                    listing.listingId()
            );


            NextStepPreparationResult preparationResult =
                    nextNegotiationStepExecutor.prepareDryRun(
                            listing,
                            decision.nextStep()
                    );


            switch (preparationResult) {

                case PREPARED -> log.warn(
                        "[NEXT STEP DRY RUN] Marketplace listing {} "
                                + "passed next-step validation. "
                                + "Step: {}, price: {}. "
                                + "The submit button was NOT clicked.",
                        listing.listingId(),
                        decision.nextStep().getStepNumber(),
                        decision.nextStep().getOfferPrice()
                );


                case OFFER_TOO_LOW -> log.warn(
                        "[NEXT STEP DRY RUN] Marketplace listing {} "
                                + "did not pass next-step validation. "
                                + "Configured price {} for step {} "
                                + "is too low. No backend changes were made.",
                        listing.listingId(),
                        decision.nextStep().getOfferPrice(),
                        decision.nextStep().getStepNumber()
                );
            }


            return false;
        }


        Long botId =
                context.getBot()
                        .getId();


        OfferQuotaReservationResponseDto quotaReservation =
                offerQuotaClient.reserveSlot(
                        botId
                );


        if (
                !quotaReservation.reserved()
        ) {

            log.warn(
                    "[NEXT STEP REAL] Daily offer quota exhausted for bot {}. "
                            + "Used: {}/{}, remaining: {}. "
                            + "Step {} for marketplace listing {} "
                            + "will NOT be sent.",
                    botId,
                    quotaReservation.used(),
                    quotaReservation.limit(),
                    quotaReservation.remaining(),
                    decision.nextStep().getStepNumber(),
                    listing.listingId()
            );


            return false;
        }


        log.warn(
                "[NEXT STEP REAL] Real next steps are enabled. "
                        + "The bot will now send step {} with price {} "
                        + "for marketplace listing {}. "
                        + "Quota after reservation: {}/{}, remaining: {}.",
                decision.nextStep().getStepNumber(),
                decision.nextStep().getOfferPrice(),
                listing.listingId(),
                quotaReservation.used(),
                quotaReservation.limit(),
                quotaReservation.remaining()
        );


        NextStepExecutionResult executionResult;


        try {

            executionResult =
                    nextNegotiationStepExecutor.sendNextStep(
                            listing,
                            decision.nextStep()
                    );

        } catch (Exception exception) {

            log.error(
                    "[NEXT STEP REAL] Failed after reserving quota for "
                            + "marketplace listing {}: {}. "
                            + "The quota slot will NOT be released because "
                            + "the delivery state is unknown.",
                    listing.listingId(),
                    getFriendlyErrorMessage(
                            exception
                    )
            );

            log.trace(
                    "[NEXT STEP REAL] Full exception for marketplace listing {}.",
                    listing.listingId(),
                    exception
            );


            throw exception;
        }


        if (
                executionResult
                        == NextStepExecutionResult.SENT
        ) {

            log.warn(
                    "[NEXT STEP REAL] Step {} was sent successfully "
                            + "for marketplace listing {}. "
                            + "The backend was updated by "
                            + "NextNegotiationStepExecutor. "
                            + "Daily quota used: {}/{}, remaining: {}.",
                    decision.nextStep().getStepNumber(),
                    listing.listingId(),
                    quotaReservation.used(),
                    quotaReservation.limit(),
                    quotaReservation.remaining()
            );


            return true;
        }


        if (
                executionResult
                        == NextStepExecutionResult.OFFER_TOO_LOW
        ) {

            releaseQuotaSlot(
                    botId,
                    listing,
                    "next-step offer too low"
            );


            log.error(
                    "[NEXT STEP REAL] Step {} was not sent for marketplace "
                            + "listing {} because Vinted rejected price {} "
                            + "as too low. Backend remains unchanged.",
                    decision.nextStep().getStepNumber(),
                    listing.listingId(),
                    decision.nextStep().getOfferPrice()
            );


            return false;
        }


        throw new IllegalStateException(
                "Unexpected next-step execution result: "
                        + executionResult
        );
    }


    private void releaseQuotaSlot(
            Long botId,
            ListingResponseDto listing,
            String reason
    ) {

        try {

            offerQuotaClient.releaseSlot(
                    botId
            );


            log.info(
                    "[OFFER QUOTA] Released quota slot for bot {} "
                            + "and marketplace listing {}. Reason: {}.",
                    botId,
                    listing.listingId(),
                    reason
            );

        } catch (Exception exception) {

            log.error(
                    "[OFFER QUOTA] Failed to release quota slot for bot {} "
                            + "and marketplace listing {}. "
                            + "The quota will remain consumed. "
                            + "Reason: {}. Error: {}",
                    botId,
                    listing.listingId(),
                    reason,
                    getFriendlyErrorMessage(
                            exception
                    )
            );

            log.trace(
                    "[OFFER QUOTA] Full release error for bot {}, listing {}.",
                    botId,
                    listing.listingId(),
                    exception
            );
        }
    }


    private String getFriendlyErrorMessage(
            Throwable exception
    ) {

        if (exception == null) {

            return "Unknown error";
        }


        String message =
                exception.getMessage();


        if (
                message == null
                        || message.isBlank()
        ) {

            return exception
                    .getClass()
                    .getSimpleName();
        }


        int firstLineEnd =
                message.indexOf('\n');


        if (
                firstLineEnd > 0
        ) {

            return message
                    .substring(
                            0,
                            firstLineEnd
                    )
                    .trim();
        }


        return message.trim();
    }
}