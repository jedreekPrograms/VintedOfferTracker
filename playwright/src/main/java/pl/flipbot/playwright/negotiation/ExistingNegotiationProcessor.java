package pl.flipbot.playwright.negotiation;

import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.listing.ListingClient;
import pl.flipbot.playwright.api.listing.ListingStatusUpdater;
import pl.flipbot.playwright.api.listing.NegotiationActivityClient;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.api.quota.OfferQuotaClient;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.model.BotConfigurationDto;
import pl.flipbot.playwright.target.VintedRateLimitException;

import java.util.List;

@Slf4j
public class ExistingNegotiationProcessor {

    private final BotContext context;
    private final ListingClient listingClient;
    private final ListingStatusUpdater listingStatusUpdater;
    private final NegotiationConversationProcessor conversationProcessor;
    private final ConversationAvailabilityDetector availabilityDetector;
    private final ConversationActivityDetector activityDetector;
    private final ConversationContactAvailabilityDetector contactAvailabilityDetector;
    private final ConsecutiveContactUnavailableTracker contactUnavailableTracker;
    private final NegotiationDecisionService decisionService;
    private final PendingNegotiationPolicy pendingPolicy;
    private final NextNegotiationStepExecutor nextStepExecutor;
    private final PreparedNextStepCoordinator preparedNextStepCoordinator;
    private final ExistingNegotiationSupport support;
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
        this.listingStatusUpdater = listingStatusUpdater;
        this.conversationProcessor = new NegotiationConversationProcessor(context);
        this.availabilityDetector = new ConversationAvailabilityDetector(context);
        this.activityDetector = new ConversationActivityDetector(context);
        this.contactAvailabilityDetector = new ConversationContactAvailabilityDetector(context);
        this.contactUnavailableTracker = new ConsecutiveContactUnavailableTracker();
        this.decisionService = new NegotiationDecisionService();
        this.pendingPolicy = new PendingNegotiationPolicy();
        this.nextStepExecutor = new NextNegotiationStepExecutor(context);
        this.preparedNextStepCoordinator = new PreparedNextStepCoordinator(context, offerQuotaClient);
        this.support = new ExistingNegotiationSupport(
                context,
                listingClient,
                listingStatusUpdater,
                new NegotiationActivityClient()
        );
        this.realNextStepsEnabled = realNextStepsEnabled;
        this.maxRealNextStepsPerRun = maxRealNextStepsPerRun;
    }

    public boolean process() {
        Long botId = context.getBot().getId();
        List<ListingResponseDto> listings = listingClient.getNegotiatingListings(botId);

        log.info("Bot {} currently has {} active negotiations", botId, listings.size());
        return inspectExistingNegotiations(listings);
    }

    private boolean inspectExistingNegotiations(List<ListingResponseDto> listings) {
        if (listings.isEmpty()) {
            log.info("[CONVERSATION] There are no active negotiations to inspect.");
            return false;
        }

        BotConfigurationDto configuration = context.getBot().getConfiguration();
        if (configuration == null) {
            throw new IllegalStateException("Bot configuration is missing");
        }

        log.info(
                "[CONVERSATION] Starting inspection of {} active negotiations.",
                listings.size()
        );

        int inspected = 0;
        int sent = 0;

        for (ListingResponseDto listing : listings) {
            try {
                inspected++;
                log.info(
                        "[CONVERSATION] Inspecting negotiation {}/{}. Backend listing {}, marketplace listing {}, conversation {}, current step {}",
                        inspected,
                        listings.size(),
                        listing.id(),
                        listing.listingId(),
                        listing.conversationId(),
                        listing.currentStep()
                );

                if (!support.matchesConfiguredTarget(listing, configuration)) {
                    ListingResponseDto finished = support.finishWrongTargetNegotiation(listing);
                    clearContactUnavailableSuspicion(listing);
                    log.error(
                            "[TARGET GUARD] Listing {} stopped as wrong target. Status={}. No additional offer was sent.",
                            listing.listingId(),
                            finished.status()
                    );
                    continue;
                }

                NegotiationConversationSnapshot snapshot = conversationProcessor.inspectSnapshot(listing);

                if (availabilityDetector.isUnavailable(listing)) {
                    ListingResponseDto unavailable = listingStatusUpdater.markNegotiationUnavailable(listing);
                    clearContactUnavailableSuspicion(listing);
                    log.warn(
                            "[AVAILABILITY] Listing {} changed from NEGOTIATING to UNAVAILABLE. No new quota slot was reserved.",
                            unavailable.listingId()
                    );
                    continue;
                }

                ConversationActivitySnapshot activity = activityDetector.inspect();
                support.logConversationActivity(listing, activity);
                support.persistConversationActivity(listing, activity, snapshot);

                boolean stepSent;
                if (snapshot.result() == NegotiationConversationResult.PENDING) {
                    PendingNegotiationDecision pending = pendingPolicy.decide(
                            listing,
                            activity,
                            configuration
                    );

                    if (pending.action() == PendingNegotiationDecision.Action.WAIT) {
                        log.info(
                                "[PENDING POLICY] Listing {} remains NEGOTIATING. Reason: {}",
                                listing.listingId(),
                                pending.reason()
                        );
                        continue;
                    }

                    if (pending.action() == PendingNegotiationDecision.Action.EXPIRE) {
                        ListingResponseDto expired = listingStatusUpdater.markExpired(listing);
                        clearContactUnavailableSuspicion(listing);
                        log.warn(
                                "[PENDING POLICY] Listing {} changed to EXPIRED. Reason: {}",
                                expired.listingId(),
                                pending.reason()
                        );
                        continue;
                    }

                    if (pending.nextStep() == null) {
                        throw new IllegalStateException(
                                "Pending policy selected SEND_NEXT_STEP without a next step"
                        );
                    }

                    stepSent = handleDecision(
                            listing,
                            snapshot,
                            NegotiationDecision.sendNextStep(
                                    pending.nextStep(),
                                    null,
                                    pending.reason()
                            )
                    );
                } else {
                    stepSent = handleDecision(
                            listing,
                            snapshot,
                            decisionService.decide(listing, snapshot, configuration)
                    );
                }

                if (stepSent) {
                    sent++;
                    log.warn(
                            "[NEXT STEP REAL] Sent next steps during this run: {}/{}",
                            sent,
                            maxRealNextStepsPerRun
                    );
                    if (sent >= maxRealNextStepsPerRun) {
                        break;
                    }
                }
            } catch (VintedRateLimitException exception) {
                /*
                 * A rate/session block is bot-wide, not a broken individual
                 * conversation. Let BotWorkerSlot pause every scheduled job.
                 * Swallowing it here used to make the run look successful.
                 */
                throw exception;
            } catch (Exception exception) {
                log.error(
                        "[CONVERSATION] Failed to inspect backend listing {}, marketplace listing {}, conversation {}: {}",
                        listing.id(),
                        listing.listingId(),
                        listing.conversationId(),
                        support.friendlyError(exception)
                );
                log.trace(
                        "[CONVERSATION] Full exception for backend listing {}.",
                        listing.id(),
                        exception
                );
            }
        }

        log.info(
                "[CONVERSATION] Finished inspection. Inspected: {}, real next steps sent: {}.",
                inspected,
                sent
        );
        return sent > 0;
    }

    private boolean handleDecision(
            ListingResponseDto listing,
            NegotiationConversationSnapshot snapshot,
            NegotiationDecision decision
    ) {
        return switch (decision.type()) {
            case WAIT -> {
                log.info(
                        "[DECISION] Listing {} remains NEGOTIATING. Result: {}. Reason: {}",
                        listing.listingId(),
                        snapshot.result(),
                        decision.reason()
                );
                yield false;
            }
            case MARK_ACTION_REQUIRED -> {
                ListingResponseDto updated = listingStatusUpdater.markActionRequired(listing, decision);
                clearContactUnavailableSuspicion(listing);
                log.warn(
                        "[DECISION] Listing {} changed to ACTION_REQUIRED at price {}. Buy now was NOT clicked. Reason: {}",
                        updated.listingId(),
                        updated.currentPrice(),
                        decision.reason()
                );
                yield false;
            }
            case SEND_NEXT_STEP -> processNextStep(listing, decision);
            case MARK_REJECTED -> {
                ListingResponseDto updated = listingStatusUpdater.markRejected(listing, decision);
                clearContactUnavailableSuspicion(listing);
                log.warn(
                        "[DECISION] Listing {} changed to REJECTED at price {}. Reason: {}",
                        updated.listingId(),
                        updated.currentPrice(),
                        decision.reason()
                );
                yield false;
            }
            case KEEP_UNKNOWN -> {
                log.warn(
                        "[DECISION] Listing {} remains NEGOTIATING because conversation state is unknown. Reason: {}",
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
            throw new IllegalStateException("Decision SEND_NEXT_STEP contains no next step");
        }

        log.warn(
                "[DECISION] Listing {} qualifies for next step. Current={}, next={}, price={}, seller counter={}. Reason: {}",
                listing.listingId(),
                listing.currentStep(),
                decision.nextStep().getStepNumber(),
                decision.nextStep().getOfferPrice(),
                decision.sellerCounterOfferPrice(),
                decision.reason()
        );

        ConversationContactAssessment contactAssessment = contactAvailabilityDetector.inspect(listing);

        if (contactAssessment.state() == ConversationContactAssessment.State.OFFER_ACTION_UNAVAILABLE) {
            clearContactUnavailableSuspicion(listing);
            log.warn(
                    "[CONTACT AVAILABILITY] Listing {} can still be messaged, but Vinted currently exposes no enabled offer action. The bot will not throw a generic timeout or reserve quota; it will retry on a later negotiation check. Reason: {}",
                    listing.listingId(),
                    contactAssessment.reason()
            );
            return false;
        }

        if (contactAssessment.state() == ConversationContactAssessment.State.CONFIRMED_UNAVAILABLE) {
            ListingResponseDto updated = listingStatusUpdater.markContactUnavailable(listing);
            clearContactUnavailableSuspicion(listing);
            log.warn(
                    "[CONTACT AVAILABILITY] Listing {} changed to CONTACT_UNAVAILABLE. The bot will stop retrying this conversation. Reason: {}",
                    updated.listingId(),
                    contactAssessment.reason()
            );
            return false;
        }

        if (contactAssessment.state() == ConversationContactAssessment.State.SUSPECTED_UNAVAILABLE) {
            int consecutiveChecks = contactUnavailableTracker.recordSuspected(
                    context.getBot().getId(),
                    listing.listingId()
            );

            if (contactUnavailableTracker.shouldClose(consecutiveChecks)) {
                ListingResponseDto updated = listingStatusUpdater.markContactUnavailable(listing);
                clearContactUnavailableSuspicion(listing);
                log.warn(
                        "[CONTACT AVAILABILITY] Listing {} changed to CONTACT_UNAVAILABLE after {} consecutive checks with no usable message composer and no offer action. This avoids an endless 2-minute retry loop. Last observation: {}",
                        updated.listingId(),
                        consecutiveChecks,
                        contactAssessment.reason()
                );
            } else {
                log.warn(
                        "[CONTACT AVAILABILITY] Listing {} may no longer allow contact. Observation {}/{}: {} No offer will be attempted this cycle; the conversation will be checked again before being closed permanently.",
                        listing.listingId(),
                        consecutiveChecks,
                        ConsecutiveContactUnavailableTracker.REQUIRED_CONSECUTIVE_SUSPECTED_CHECKS,
                        contactAssessment.reason()
                );
            }

            return false;
        }

        clearContactUnavailableSuspicion(listing);

        if (realNextStepsEnabled) {
            return preparedNextStepCoordinator.execute(listing, decision);
        }

        log.warn(
                "[NEXT STEP DRY RUN] Real next steps are disabled. Preparing step {} for listing {} without sending it.",
                decision.nextStep().getStepNumber(),
                listing.listingId()
        );

        NextStepPreparationResult result = nextStepExecutor.prepareDryRun(
                listing,
                decision.nextStep()
        );

        if (result == NextStepPreparationResult.PREPARED) {
            log.warn(
                    "[NEXT STEP DRY RUN] Listing {} passed validation. Step={}, price={}. Submit was NOT clicked.",
                    listing.listingId(),
                    decision.nextStep().getStepNumber(),
                    decision.nextStep().getOfferPrice()
            );
        } else {
            log.warn(
                    "[NEXT STEP DRY RUN] Listing {} failed validation because price {} for step {} is too low. Backend unchanged.",
                    listing.listingId(),
                    decision.nextStep().getOfferPrice(),
                    decision.nextStep().getStepNumber()
            );
        }
        return false;
    }

    private void clearContactUnavailableSuspicion(ListingResponseDto listing) {
        contactUnavailableTracker.clear(
                context.getBot().getId(),
                listing.listingId()
        );
    }
}
