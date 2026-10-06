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
    private final TerminalNegotiationRecoveryPolicy terminalRecoveryPolicy;
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
        this.terminalRecoveryPolicy = new TerminalNegotiationRecoveryPolicy();
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

        /*
         * Active conversations always have priority. Terminal recovery is a
         * bounded safety net and must never delay normal negotiations.
         */
        List<ListingResponseDto> listings =
                listingClient.getNegotiatingListings(botId);

        log.info(
                "Bot {} currently has {} active negotiations",
                botId,
                listings.size()
        );

        boolean sentAny = inspectExistingNegotiations(listings);

        List<ListingResponseDto> recoveryCandidates =
                listingClient.getNegotiationRecoveryCandidates(botId);
        inspectTerminalRecoveryCandidates(recoveryCandidates);

        return sentAny;
    }

    private void inspectTerminalRecoveryCandidates(
            List<ListingResponseDto> candidates
    ) {
        if (candidates == null || candidates.isEmpty()) {
            return;
        }

        BotConfigurationDto currentProductConfiguration =
                context.getBot().getConfiguration();
        if (currentProductConfiguration == null) {
            throw new IllegalStateException("Bot configuration is missing");
        }

        log.info(
                "[NEGOTIATION RECOVERY] Inspecting {} recent terminal conversation candidate(s) read-only before active negotiations.",
                candidates.size()
        );

        for (ListingResponseDto listing : candidates) {
            BotConfigurationDto configuration =
                    NegotiationStrategyConfigurationResolver.resolve(
                            currentProductConfiguration,
                            listing.negotiationStrategySnapshot()
                    );

            context.getBot().setConfiguration(configuration);
            boolean transitioned = false;

            try {
                NegotiationConversationSnapshot snapshot =
                        conversationProcessor.inspectRecoverySnapshot(listing);

                TerminalNegotiationRecoveryDecision recovery =
                        terminalRecoveryPolicy.decide(
                                listing,
                                snapshot,
                                configuration
                        );

                switch (recovery.action()) {
                    case KEEP_TERMINAL -> log.info(
                            "[NEGOTIATION RECOVERY] Listing {} stays {}. Live result={}. Reason: {}",
                            listing.listingId(),
                            listing.status(),
                            snapshot.result(),
                            recovery.reason()
                    );

                    case REOPEN_NEGOTIATING -> {
                        ListingResponseDto reopened =
                                listingClient.reopenNegotiationForRecovery(
                                        context.getBot().getId(),
                                        listing.id(),
                                        recovery.awaitingSellerResponse(),
                                        recovery.reason()
                                );
                        transitioned = true;

                        log.warn(
                                "[NEGOTIATION RECOVERY] Listing {} recovered from {} to {} at step {}. No offer was sent by the recovery layer.",
                                reopened.listingId(),
                                listing.status(),
                                reopened.status(),
                                reopened.currentStep()
                        );
                    }

                    case MARK_ACTION_REQUIRED -> {
                        ListingResponseDto updated =
                                listingStatusUpdater.markActionRequired(
                                        listing,
                                        recovery.negotiationDecision()
                                );
                        transitioned = true;
                        clearContactUnavailableSuspicion(listing);

                        log.warn(
                                "[NEGOTIATION RECOVERY] Fresh seller decision recovered from terminal history. Listing {} changed from {} to ACTION_REQUIRED at price {}. No buyer offer was sent. Reason: {}",
                                updated.listingId(),
                                listing.status(),
                                updated.currentPrice(),
                                recovery.reason()
                        );
                    }
                }
            } catch (VintedRateLimitException exception) {
                throw exception;
            } catch (Exception exception) {
                log.error(
                        "[NEGOTIATION RECOVERY] Failed to inspect terminal backend listing {}, marketplace listing {}, conversation {}: {}",
                        listing.id(),
                        listing.listingId(),
                        listing.conversationId(),
                        support.friendlyError(exception)
                );
                log.trace(
                        "[NEGOTIATION RECOVERY] Full exception for backend listing {}.",
                        listing.id(),
                        exception
                );
            } finally {
                if (!transitioned) {
                    try {
                        listingClient.markNegotiationRecoveryChecked(
                                context.getBot().getId(),
                                listing.id()
                        );
                    } catch (Exception exception) {
                        log.warn(
                                "[NEGOTIATION RECOVERY] Could not persist recovery throttle for backend listing {}: {}",
                                listing.id(),
                                support.friendlyError(exception)
                        );
                    }
                }

                context.getBot().setConfiguration(
                        currentProductConfiguration
                );
            }
        }
    }

    private boolean inspectExistingNegotiations(List<ListingResponseDto> listings) {
        if (listings.isEmpty()) {
            log.info("[CONVERSATION] There are no active negotiations to inspect.");
            return false;
        }

        BotConfigurationDto currentProductConfiguration =
                context.getBot().getConfiguration();
        if (currentProductConfiguration == null) {
            throw new IllegalStateException("Bot configuration is missing");
        }

        log.info(
                "[CONVERSATION] Starting inspection of {} active negotiations.",
                listings.size()
        );

        int inspected = 0;
        int sent = 0;

        for (ListingResponseDto listing : listings) {
            BotConfigurationDto configuration =
                    NegotiationStrategyConfigurationResolver.resolve(
                            currentProductConfiguration,
                            listing.negotiationStrategySnapshot()
                    );
            context.getBot().setConfiguration(configuration);

            try {
                inspected++;
                log.info(
                        "[CONVERSATION] Inspecting negotiation {}/{}. Backend listing {}, marketplace listing {}, conversation {}, current step {}, strategy v{}",
                        inspected,
                        listings.size(),
                        listing.id(),
                        listing.listingId(),
                        listing.conversationId(),
                        listing.currentStep(),
                        listing.negotiationStrategyVersion()
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

                if ((snapshot.result() == NegotiationConversationResult.PENDING
                        || snapshot.result() == NegotiationConversationResult.UNKNOWN
                        || snapshot.result() == NegotiationConversationResult.REJECTED
                        || snapshot.result() == NegotiationConversationResult.CANCELLED)
                        && closeIfNegotiationControlsUnavailable(listing)) {
                    continue;
                }

                if (snapshot.result() == NegotiationConversationResult.CANCELLED) {
                    ListingResponseDto expired =
                            listingStatusUpdater.markExpired(listing);
                    clearContactUnavailableSuspicion(listing);
                    log.warn(
                            "[CONVERSATION] Listing {} changed from NEGOTIATING to EXPIRED because Vinted marks the latest own offer as CANCELLED ('{}'). No quota or follow-up offer will be attempted.",
                            expired.listingId(),
                            snapshot.rawStatus()
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

                    switch (pending.action()) {
                        case WAIT -> log.info(
                                "[PENDING POLICY] Listing {} remains NEGOTIATING. Reason: {}",
                                listing.listingId(),
                                pending.reason()
                        );
                        case EXPIRE -> {
                            ListingResponseDto expired =
                                    listingStatusUpdater.markExpired(listing);
                            clearContactUnavailableSuspicion(listing);
                            log.warn(
                                    "[PENDING POLICY] Listing {} changed from NEGOTIATING to EXPIRED. Reason: {}",
                                    expired.listingId(),
                                    pending.reason()
                            );
                        }
                        case SEND_NEXT_STEP -> {
                            stepSent = processNextStep(
                                    listing,
                                    NegotiationDecision.sendNextStep(
                                            pending.nextStep(),
                                            null,
                                            pending.reason()
                                    )
                            );

                            if (stepSent) {
                                sent++;
                                log.warn(
                                        "[NEXT STEP REAL] Sent next steps during this run: {}/{}",
                                        sent,
                                        maxRealNextStepsPerRun
                                );
                                if (sent >= maxRealNextStepsPerRun) {
                                    return true;
                                }
                            }
                        }
                    }
                    continue;
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
            } finally {
                context.getBot().setConfiguration(currentProductConfiguration);
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

        /*
         * Re-check immediately before an actual next-step preparation. This
         * also covers SELLER_COUNTER_OFFER paths that legitimately bypass the
         * generic missing-offer preflight until a new buyer offer is needed.
         */
        if (closeIfNegotiationControlsUnavailable(listing)) {
            return false;
        }

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

    private boolean closeIfNegotiationControlsUnavailable(
            ListingResponseDto listing
    ) {
        ConversationContactAssessment contactAssessment =
                contactAvailabilityDetector.inspect(listing);

        return switch (contactAssessment.state()) {
            case AVAILABLE -> {
                clearContactUnavailableSuspicion(listing);
                yield false;
            }
            case OFFER_ACTION_UNAVAILABLE -> {
                /*
                 * A conversation may still allow ordinary chat after the item
                 * has been sold/removed. For an automated negotiation, an
                 * absent/disabled "make offer" action means there is no longer
                 * an actionable negotiation to keep in the active pool.
                 */
                ListingResponseDto updated =
                        listingStatusUpdater.markNegotiationUnavailable(listing);
                clearContactUnavailableSuspicion(listing);
                log.warn(
                        "[CONTACT AVAILABILITY] Listing {} changed from NEGOTIATING to UNAVAILABLE because Vinted exposes no enabled negotiation offer action, even though ordinary messaging may still be available. Reason: {}",
                        updated.listingId(),
                        contactAssessment.reason()
                );
                yield true;
            }
            case CONFIRMED_UNAVAILABLE -> {
                ListingResponseDto updated =
                        listingStatusUpdater.markContactUnavailable(listing);
                clearContactUnavailableSuspicion(listing);
                log.warn(
                        "[CONTACT AVAILABILITY] Listing {} changed to CONTACT_UNAVAILABLE because Vinted exposed explicit contact-disabled/block evidence. Reason: {}",
                        updated.listingId(),
                        contactAssessment.reason()
                );
                yield true;
            }
            case SUSPECTED_UNAVAILABLE -> {
                int consecutiveChecks =
                        contactUnavailableTracker.recordSuspected(
                                context.getBot().getId(),
                                listing.listingId()
                        );

                if (contactUnavailableTracker.shouldClose(
                        consecutiveChecks
                )) {
                    ListingResponseDto updated =
                            listingStatusUpdater.markNegotiationUnavailable(
                                    listing
                            );
                    clearContactUnavailableSuspicion(listing);
                    log.warn(
                            "[CONTACT AVAILABILITY] Listing {} changed from NEGOTIATING to UNAVAILABLE after {} consecutive checks with no usable negotiation or message controls. Last observation: {}",
                            updated.listingId(),
                            consecutiveChecks,
                            contactAssessment.reason()
                    );
                    yield true;
                }

                log.warn(
                        "[CONTACT AVAILABILITY] Listing {} temporarily remains NEGOTIATING because the conversation controls are ambiguous. Observation count={}/{}. No quota or offer will be attempted from this check. Last observation: {}",
                        listing.listingId(),
                        consecutiveChecks,
                        ConsecutiveContactUnavailableTracker.REQUIRED_CONSECUTIVE_SUSPECTED_CHECKS,
                        contactAssessment.reason()
                );
                yield false;
            }
        };
    }

    private void clearContactUnavailableSuspicion(ListingResponseDto listing) {
        contactUnavailableTracker.clear(
                context.getBot().getId(),
                listing.listingId()
        );
    }
}