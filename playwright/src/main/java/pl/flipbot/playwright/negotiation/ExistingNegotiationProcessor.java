package pl.flipbot.playwright.negotiation;

import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.listing.ListingClient;
import pl.flipbot.playwright.api.listing.ListingStatusUpdater;
import pl.flipbot.playwright.api.listing.NegotiationActivityClient;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.api.listing.dto.NegotiationActivityRequestDto;
import pl.flipbot.playwright.api.listing.dto.UpdateListingRequestDto;
import pl.flipbot.playwright.api.quota.OfferQuotaClient;
import pl.flipbot.playwright.api.quota.dto.OfferQuotaReservationResponseDto;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.model.BotConfigurationDto;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

@Slf4j
public class ExistingNegotiationProcessor {

    private final BotContext context;
    private final ListingClient listingClient;
    private final OfferQuotaClient offerQuotaClient;
    private final ListingStatusUpdater listingStatusUpdater;
    private final NegotiationConversationProcessor negotiationConversationProcessor;
    private final ConversationAvailabilityDetector conversationAvailabilityDetector;
    private final ConversationActivityDetector conversationActivityDetector;
    private final NegotiationActivityClient negotiationActivityClient;
    private final NegotiationDecisionService negotiationDecisionService;
    private final PendingNegotiationPolicy pendingNegotiationPolicy;
    private final NextNegotiationStepExecutor nextNegotiationStepExecutor;
    private final NextStepActionGuardCoordinator nextStepActionGuardCoordinator;
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
        this.context = context;
        this.listingClient = listingClient;
        this.offerQuotaClient = offerQuotaClient;
        this.listingStatusUpdater = listingStatusUpdater;
        this.negotiationConversationProcessor =
                new NegotiationConversationProcessor(context);
        this.conversationAvailabilityDetector =
                new ConversationAvailabilityDetector(context);
        this.conversationActivityDetector =
                new ConversationActivityDetector(context);
        this.negotiationActivityClient = new NegotiationActivityClient();
        this.negotiationDecisionService = new NegotiationDecisionService();
        this.pendingNegotiationPolicy = new PendingNegotiationPolicy();
        this.nextNegotiationStepExecutor =
                new NextNegotiationStepExecutor(context);
        this.nextStepActionGuardCoordinator =
                new NextStepActionGuardCoordinator();
        this.realNextStepsEnabled = realNextStepsEnabled;
        this.maxRealNextStepsPerRun = maxRealNextStepsPerRun;
    }

    public boolean process() {
        Long botId = context.getBot().getId();

        List<ListingResponseDto> negotiatingListings =
                listingClient.getNegotiatingListings(botId);

        log.info(
                "Bot {} currently has {} active negotiations",
                botId,
                negotiatingListings.size()
        );

        return inspectExistingNegotiations(negotiatingListings);
    }

    private boolean inspectExistingNegotiations(
            List<ListingResponseDto> negotiatingListings
    ) {
        if (negotiatingListings.isEmpty()) {
            log.info(
                    "[CONVERSATION] There are no active negotiations to inspect."
            );
            return false;
        }

        BotConfigurationDto configuration =
                context.getBot().getConfiguration();

        if (configuration == null) {
            throw new IllegalStateException(
                    "Bot configuration is missing"
            );
        }

        log.info(
                "[CONVERSATION] Starting inspection of {} active negotiations.",
                negotiatingListings.size()
        );

        int inspectedCount = 0;
        int sentNextSteps = 0;

        for (ListingResponseDto listing : negotiatingListings) {
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

                if (!matchesConfiguredTarget(listing, configuration)) {
                    ListingResponseDto finishedListing =
                            finishWrongTargetNegotiation(listing);

                    log.error(
                            "[TARGET GUARD] Existing negotiation for marketplace "
                                    + "listing {} was stopped because it does not "
                                    + "match the configured target. "
                                    + "Configured brand='{}', model='{}'. "
                                    + "Listing title='{}'. "
                                    + "Status changed from NEGOTIATING to FINISHED. "
                                    + "No new quota slot was reserved and no "
                                    + "additional offer was sent.",
                            listing.listingId(),
                            configuration.getBrand(),
                            configuration.getModel(),
                            listing.title()
                    );

                    log.info(
                            "[TARGET GUARD] Backend listing {} is now {} "
                                    + "and no longer occupies an active "
                                    + "NEGOTIATING slot.",
                            finishedListing.id(),
                            finishedListing.status()
                    );
                    continue;
                }

                NegotiationConversationSnapshot snapshot =
                        negotiationConversationProcessor.inspectSnapshot(listing);

                if (conversationAvailabilityDetector.isUnavailable(listing)) {
                    ListingResponseDto unavailableListing =
                            listingStatusUpdater.markNegotiationUnavailable(listing);

                    log.warn(
                            "[AVAILABILITY] Marketplace listing {} was changed "
                                    + "from NEGOTIATING to UNAVAILABLE because "
                                    + "Vinted reports that the item was sold "
                                    + "or removed. No new quota slot was reserved.",
                            unavailableListing.listingId()
                    );
                    continue;
                }

                ConversationActivitySnapshot activitySnapshot =
                        conversationActivityDetector.inspect();

                logConversationActivity(listing, activitySnapshot);
                persistConversationActivity(listing, activitySnapshot);

                boolean stepWasSent;

                if (snapshot.result() == NegotiationConversationResult.PENDING) {
                    PendingNegotiationDecision pendingDecision =
                            pendingNegotiationPolicy.decide(
                                    listing,
                                    activitySnapshot,
                                    configuration
                            );

                    if (pendingDecision.action()
                            == PendingNegotiationDecision.Action.WAIT) {
                        log.info(
                                "[PENDING POLICY] Marketplace listing {} "
                                        + "remains NEGOTIATING. Reason: {}",
                                listing.listingId(),
                                pendingDecision.reason()
                        );
                        continue;
                    }

                    if (pendingDecision.action()
                            == PendingNegotiationDecision.Action.EXPIRE) {
                        ListingResponseDto expiredListing =
                                listingStatusUpdater.markExpired(listing);

                        log.warn(
                                "[PENDING POLICY] Marketplace listing {} "
                                        + "was changed from NEGOTIATING to EXPIRED. "
                                        + "It no longer occupies an active "
                                        + "negotiation slot. No quota slot was "
                                        + "released because previously sent offers "
                                        + "remain real Vinted offers. Reason: {}",
                                expiredListing.listingId(),
                                pendingDecision.reason()
                        );
                        continue;
                    }

                    if (pendingDecision.nextStep() == null) {
                        throw new IllegalStateException(
                                "Pending policy selected SEND_NEXT_STEP "
                                        + "without a next step for backend listing "
                                        + listing.id()
                        );
                    }

                    NegotiationDecision timedDecision =
                            NegotiationDecision.sendNextStep(
                                    pendingDecision.nextStep(),
                                    null,
                                    pendingDecision.reason()
                            );

                    stepWasSent =
                            handleNegotiationDecision(
                                    listing,
                                    snapshot,
                                    timedDecision
                            );
                } else {
                    NegotiationDecision decision =
                            negotiationDecisionService.decide(
                                    listing,
                                    snapshot,
                                    configuration
                            );

                    stepWasSent =
                            handleNegotiationDecision(
                                    listing,
                                    snapshot,
                                    decision
                            );
                }

                if (stepWasSent) {
                    sentNextSteps++;

                    log.warn(
                            "[NEXT STEP REAL] Sent next steps during "
                                    + "this run: {}/{}",
                            sentNextSteps,
                            maxRealNextStepsPerRun
                    );

                    if (sentNextSteps >= maxRealNextStepsPerRun) {
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
                        getFriendlyErrorMessage(exception)
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

    private void logConversationActivity(
            ListingResponseDto listing,
            ConversationActivitySnapshot activitySnapshot
    ) {
        if (!activitySnapshot.inspectionSucceeded()) {
            log.debug(
                    "[CONVERSATION ACTIVITY] Activity inspection "
                            + "was unavailable for marketplace listing {}.",
                    listing.listingId()
            );
            return;
        }

        if (!activitySnapshot.latestOwnOfferFound()) {
            log.debug(
                    "[CONVERSATION ACTIVITY] No own offer was found "
                            + "in conversation for marketplace listing {}.",
                    listing.listingId()
            );
            return;
        }

        if (activitySnapshot.sellerMessageAfterLatestOwnOffer()) {
            log.info(
                    "[CONVERSATION ACTIVITY] Marketplace listing {} "
                            + "has a normal seller message after "
                            + "the latest own offer. "
                            + "Latest seller message at {}: {}",
                    listing.listingId(),
                    activitySnapshot.latestSellerMessageAt(),
                    abbreviate(
                            activitySnapshot.latestSellerMessageText(),
                            160
                    )
            );
        }

        if (activitySnapshot.readIndicatorAfterLatestOwnOffer()) {
            log.info(
                    "[CONVERSATION ACTIVITY] Marketplace listing {} "
                            + "shows the Vinted read indicator after "
                            + "the latest own offer.",
                    listing.listingId()
            );
        }

        if (!activitySnapshot.sellerMessageAfterLatestOwnOffer()
                && !activitySnapshot.readIndicatorAfterLatestOwnOffer()) {
            log.debug(
                    "[CONVERSATION ACTIVITY] Marketplace listing {} "
                            + "has no normal seller message and no read "
                            + "indicator after the latest own offer.",
                    listing.listingId()
            );
        }
    }

    private void persistConversationActivity(
            ListingResponseDto listing,
            ConversationActivitySnapshot activitySnapshot
    ) {
        if (!activitySnapshot.inspectionSucceeded()
                || !activitySnapshot.latestOwnOfferFound()) {
            return;
        }

        boolean sellerActivityCanBePersisted =
                activitySnapshot.sellerMessageAfterLatestOwnOffer()
                        && activitySnapshot.latestSellerMessageAt() != null;

        boolean readDetected =
                activitySnapshot.readIndicatorAfterLatestOwnOffer();

        if (!sellerActivityCanBePersisted && !readDetected) {
            return;
        }

        NegotiationActivityRequestDto request =
                new NegotiationActivityRequestDto(
                        sellerActivityCanBePersisted
                                ? activitySnapshot.latestSellerMessageAt()
                                : null,
                        readDetected
                );

        try {
            negotiationActivityClient.recordActivity(
                    context.getBot().getId(),
                    listing.id(),
                    request
            );
        } catch (Exception exception) {
            log.warn(
                    "[NEGOTIATION ACTIVITY API] Could not persist "
                            + "activity for backend listing {}, "
                            + "marketplace listing {}: {}",
                    listing.id(),
                    listing.listingId(),
                    getFriendlyErrorMessage(exception)
            );

            log.trace(
                    "[NEGOTIATION ACTIVITY API] Full persistence "
                            + "exception for backend listing {}.",
                    listing.id(),
                    exception
            );
        }
    }

    private String abbreviate(
            String value,
            int maximumLength
    ) {
        if (value == null) {
            return "<none>";
        }

        String normalized =
                value.replaceAll("\\s+", " ").trim();

        if (normalized.length() <= maximumLength) {
            return normalized;
        }

        return normalized.substring(0, maximumLength) + "...";
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
                    processNextStep(listing, decision);

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

        Long botId = context.getBot().getId();

        var guardRequestId =
                nextStepActionGuardCoordinator.acquire(
                        botId,
                        listing,
                        decision.nextStep()
                );

        if (guardRequestId == null) {
            log.error(
                    "[NEXT STEP REAL] Persistent NEXT_STEP guard blocked "
                            + "marketplace listing {}. No quota was reserved "
                            + "and no real submit was attempted.",
                    listing.listingId()
            );
            return false;
        }

        OfferQuotaReservationResponseDto quotaReservation;

        try {
            quotaReservation =
                    offerQuotaClient.reserveSlot(botId);
        } catch (Exception exception) {
            nextStepActionGuardCoordinator.releaseBeforeSubmitOrRetrySafely(
                    botId,
                    listing,
                    guardRequestId,
                    "quota reservation failed before real submit"
            );
            throw exception;
        }

        if (!quotaReservation.reserved()) {
            nextStepActionGuardCoordinator.releaseBeforeSubmitOrRetrySafely(
                    botId,
                    listing,
                    guardRequestId,
                    "daily quota not reserved"
            );

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
                        + "Persistent guard and quota are reserved. "
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
                    "[NEXT STEP REAL] Failed after reserving quota and "
                            + "entering the real next-step flow for marketplace "
                            + "listing {}: {}. The quota slot and persistent "
                            + "guard will NOT be released because delivery "
                            + "state is unknown.",
                    listing.listingId(),
                    getFriendlyErrorMessage(exception)
            );

            log.trace(
                    "[NEXT STEP REAL] Full exception for marketplace listing {}.",
                    listing.listingId(),
                    exception
            );
            throw exception;
        }

        if (executionResult == NextStepExecutionResult.SENT) {
            nextStepActionGuardCoordinator
                    .releaseAfterConfirmedSuccessBestEffort(
                            botId,
                            listing,
                            guardRequestId
                    );

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

        if (executionResult == NextStepExecutionResult.OFFER_TOO_LOW) {
            releaseQuotaSlot(
                    botId,
                    listing,
                    "next-step offer too low"
            );

            nextStepActionGuardCoordinator.releaseBeforeSubmitOrRetrySafely(
                    botId,
                    listing,
                    guardRequestId,
                    "next-step offer too low before submit"
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
            offerQuotaClient.releaseSlot(botId);

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
                    getFriendlyErrorMessage(exception)
            );

            log.trace(
                    "[OFFER QUOTA] Full release error for bot {}, listing {}.",
                    botId,
                    listing.listingId(),
                    exception
            );
        }
    }

    private boolean matchesConfiguredTarget(
            ListingResponseDto listing,
            BotConfigurationDto configuration
    ) {
        String configuredModel =
                normalizeTargetValue(configuration.getModel());

        if (configuredModel.isBlank()) {
            return true;
        }

        String configuredBrand =
                normalizeTargetValue(configuration.getBrand());

        String expectedTitle;

        if (configuredBrand.isBlank()
                || configuredModel.equals(configuredBrand)
                || configuredModel.startsWith(configuredBrand + " ")) {
            expectedTitle = configuredModel;
        } else {
            expectedTitle = configuredBrand + " " + configuredModel;
        }

        String actualTitle =
                normalizeTargetValue(listing.title());

        boolean matches = expectedTitle.equals(actualTitle);

        if (!matches) {
            log.error(
                    "[TARGET GUARD] Target mismatch detected before opening "
                            + "conversation. Backend listing {}, marketplace "
                            + "listing {}. Expected normalized title='{}', "
                            + "actual normalized title='{}'.",
                    listing.id(),
                    listing.listingId(),
                    expectedTitle,
                    actualTitle
            );
        }

        return matches;
    }

    private ListingResponseDto finishWrongTargetNegotiation(
            ListingResponseDto listing
    ) {
        if (listing.currentStep() == null
                || listing.currentStep() <= 0) {
            throw new IllegalStateException(
                    "Cannot finish wrong-target negotiation because backend "
                            + "listing "
                            + listing.id()
                            + " has an invalid current step: "
                            + listing.currentStep()
            );
        }

        if (listing.currentPrice() == null
                && listing.originalPrice() == null) {
            throw new IllegalStateException(
                    "Cannot finish wrong-target negotiation because backend "
                            + "listing "
                            + listing.id()
                            + " has no current or original price"
            );
        }

        UpdateListingRequestDto request =
                new UpdateListingRequestDto(
                        "FINISHED",
                        listing.currentPrice() != null
                                ? listing.currentPrice()
                                : listing.originalPrice(),
                        listing.currentStep(),
                        false,
                        listing.conversationId(),
                        listing.conversationUrl()
                );

        ListingResponseDto updatedListing =
                listingClient.updateListing(
                        context.getBot().getId(),
                        listing.id(),
                        request
                );

        if (!"FINISHED".equals(updatedListing.status())) {
            throw new IllegalStateException(
                    "Backend returned an unexpected status after stopping "
                            + "wrong-target negotiation. Expected FINISHED, "
                            + "actual: "
                            + updatedListing.status()
            );
        }

        if (Boolean.TRUE.equals(
                updatedListing.awaitingSellerResponse()
        )) {
            throw new IllegalStateException(
                    "Backend listing "
                            + listing.id()
                            + " still has awaitingSellerResponse=true "
                            + "after wrong-target negotiation was stopped"
            );
        }

        return updatedListing;
    }

    private String normalizeTargetValue(String value) {
        if (value == null) {
            return "";
        }

        String preparedValue =
                value.replace("+", " plus ")
                        .replace("＋", " plus ");

        String withoutDiacritics =
                Normalizer.normalize(
                                preparedValue,
                                Normalizer.Form.NFD
                        )
                        .replaceAll("\\p{M}+", "");

        return withoutDiacritics
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private String getFriendlyErrorMessage(Throwable exception) {
        if (exception == null) {
            return "Unknown error";
        }

        String message = exception.getMessage();

        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }

        int firstLineEnd = message.indexOf('\n');

        if (firstLineEnd > 0) {
            return message.substring(0, firstLineEnd).trim();
        }

        return message.trim();
    }
}
