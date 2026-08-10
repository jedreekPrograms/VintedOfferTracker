package pl.flipbot.playwright.worker;

import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.listing.ListingClient;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.api.listing.dto.UpdateListingRequestDto;
import pl.flipbot.playwright.browser.BrowserManager;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.filters.FilterService;
import pl.flipbot.playwright.login.LoginService;
import pl.flipbot.playwright.marketplace.MarketplaceNavigator;
import pl.flipbot.playwright.model.BotConfigurationDto;
import pl.flipbot.playwright.model.BotDetailsDto;
import pl.flipbot.playwright.negotiation.NegotiationConversationProcessor;
import pl.flipbot.playwright.negotiation.NegotiationConversationSnapshot;
import pl.flipbot.playwright.negotiation.NegotiationDecision;
import pl.flipbot.playwright.negotiation.NegotiationDecisionService;
import pl.flipbot.playwright.negotiation.NegotiationExecutor;
import pl.flipbot.playwright.negotiation.NegotiationStartResult;
import pl.flipbot.playwright.negotiation.NextNegotiationStepExecutor;
import pl.flipbot.playwright.negotiation.NextStepExecutionResult;
import pl.flipbot.playwright.negotiation.NextStepPreparationResult;
import pl.flipbot.playwright.processing.ListingProcessingService;
import pl.flipbot.playwright.scanner.ListingScanner;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Slf4j
public class BotWorker implements Runnable {

    /*
     * Rozpoczynanie nowych negocjacji
     * dla listingów DISCOVERED.
     */
    private static final boolean REAL_OFFERS_ENABLED =
            false;

    /*
     * Wysyłanie kroku 2, 3 itd.
     * w istniejących rozmowach.
     */
    private static final boolean REAL_NEXT_STEPS_ENABLED =
            false;

    private static final int MAX_REAL_OFFERS_PER_RUN =
            1;

    private static final int MAX_REAL_NEXT_STEPS_PER_RUN =
            1;

    private final BotContext context;

    private final LoginService loginService;

    private final MarketplaceNavigator marketplaceNavigator;

    private final FilterService filterService;

    private final ListingScanner listingScanner;

    private final ListingProcessingService
            listingProcessingService;

    private final ListingClient listingClient;

    private final NegotiationExecutor negotiationExecutor;

    private final NegotiationConversationProcessor
            negotiationConversationProcessor;

    private final NegotiationDecisionService
            negotiationDecisionService;

    private final NextNegotiationStepExecutor
            nextNegotiationStepExecutor;

    public BotWorker(
            BotDetailsDto bot,
            BrowserManager browserManager
    ) {

        this.context =
                new BotContext(
                        bot,
                        browserManager
                );

        this.loginService =
                new LoginService(
                        context
                );

        this.marketplaceNavigator =
                new MarketplaceNavigator(
                        context
                );

        this.filterService =
                new FilterService(
                        context
                );

        this.listingScanner =
                new ListingScanner(
                        context
                );

        this.listingClient =
                new ListingClient();

        this.listingProcessingService =
                new ListingProcessingService(
                        context,
                        listingClient
                );

        this.negotiationExecutor =
                new NegotiationExecutor(
                        context
                );

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
    }

    @Override
    public void run() {

        log.info(
                "Worker started for bot {}",
                context.getBot().getId()
        );

        try {

            loginService.login();

            while (
                    !Thread.currentThread()
                            .isInterrupted()
            ) {

                doWork();

                Thread.sleep(
                        30_000
                );
            }

        } catch (InterruptedException exception) {

            Thread.currentThread()
                    .interrupt();

            log.info(
                    "Worker {} was interrupted",
                    context.getBot().getId()
            );

        } catch (Exception exception) {

            log.error(
                    "Worker {} failed",
                    context.getBot().getId(),
                    exception
            );

        } finally {

            context.close();

            log.info(
                    "Worker stopped for bot {}",
                    context.getBot().getId()
            );
        }
    }

    private void doWork() {

        Long botId =
                context.getBot().getId();

        List<ListingResponseDto> negotiatingListings =
                listingClient.getNegotiatingListings(
                        botId
                );

        log.info(
                "Bot {} currently has {} active negotiations",
                botId,
                negotiatingListings.size()
        );

        boolean realNextStepWasSent =
                inspectExistingNegotiations(
                        negotiatingListings
                );

        /*
         * Po wysłaniu prawdziwego kolejnego
         * kroku kończymy cykl.
         */
        if (realNextStepWasSent) {

            log.warn(
                    "[NEXT STEP REAL] A real next negotiation step "
                            + "was sent during this run. "
                            + "The worker will not scan the catalog "
                            + "or start another negotiation."
            );

            return;
        }

        marketplaceNavigator.goToCatalog();

        filterService.applyFilters(
                context.getBot()
        );

        var scannedListings =
                listingScanner.scan();

        var newlyClaimedListings =
                listingProcessingService.process(
                        scannedListings
                );

        log.info(
                "Bot {} claimed {} new listings during this scan",
                botId,
                newlyClaimedListings.size()
        );

        List<ListingResponseDto> discoveredListings =
                listingClient.getDiscoveredListings(
                        botId
                );

        int allowedNewNegotiations =
                listingClient.getAllowedNewNegotiations(
                        botId
                );

        if (allowedNewNegotiations <= 0) {

            log.info(
                    "Bot {} cannot start any new negotiations",
                    botId
            );

            return;
        }

        if (discoveredListings.isEmpty()) {

            log.info(
                    "Bot {} has no discovered listings",
                    botId
            );

            return;
        }

        if (!REAL_OFFERS_ENABLED) {

            log.warn(
                    "[REAL OFFER] Real offers are disabled. "
                            + "No new negotiation will be started."
            );

            return;
        }

        int maximumOffersThisRun =
                Math.min(
                        allowedNewNegotiations,
                        MAX_REAL_OFFERS_PER_RUN
                );

        log.warn(
                "[REAL OFFER] Real offers are enabled. "
                        + "Bot {} has {} discovered listings and may start {} "
                        + "new negotiations. This run is limited to {} "
                        + "real offer.",
                botId,
                discoveredListings.size(),
                allowedNewNegotiations,
                maximumOffersThisRun
        );

        int checkedListings =
                0;

        int startedNegotiations =
                0;

        for (
                ListingResponseDto listing
                : discoveredListings
        ) {

            if (
                    startedNegotiations
                            >= maximumOffersThisRun
            ) {

                break;
            }

            checkedListings++;

            log.info(
                    "[REAL OFFER] Checking candidate {}. "
                            + "Backend listing {}, marketplace listing {}",
                    checkedListings,
                    listing.id(),
                    listing.listingId()
            );

            NegotiationStartResult result =
                    negotiationExecutor
                            .startFirstNegotiation(
                                    listing
                            );

            if (
                    result
                            == NegotiationStartResult
                            .LISTING_UNAVAILABLE
            ) {

                markUnavailable(
                        botId,
                        listing
                );

                continue;
            }

            if (
                    result
                            == NegotiationStartResult
                            .OFFER_TOO_LOW
            ) {

                markOfferTooLow(
                        botId,
                        listing
                );

                continue;
            }

            if (
                    result
                            == NegotiationStartResult
                            .STARTED
            ) {

                startedNegotiations++;

                log.warn(
                        "[REAL OFFER] A real negotiation was started "
                                + "for marketplace listing {}. "
                                + "Started negotiations during this run: {}.",
                        listing.listingId(),
                        startedNegotiations
                );

                return;
            }

            throw new IllegalStateException(
                    "Unexpected negotiation start result: "
                            + result
            );
        }

        log.info(
                "[REAL OFFER] Checked {} candidates. "
                        + "Started {} real negotiations.",
                checkedListings,
                startedNegotiations
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
                            MAX_REAL_NEXT_STEPS_PER_RUN
                    );

                    if (
                            sentNextSteps
                                    >= MAX_REAL_NEXT_STEPS_PER_RUN
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
                        "[CONVERSATION] Failed to inspect or process "
                                + "backend listing {}, marketplace listing {}, "
                                + "conversation {}. "
                                + "Check the conversation manually before "
                                + "running real next steps again.",
                        listing.id(),
                        listing.listingId(),
                        listing.conversationId(),
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
                        markActionRequired(
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
                        markRejected(
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

        if (!REAL_NEXT_STEPS_ENABLED) {

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

        log.warn(
                "[NEXT STEP REAL] Real next steps are enabled. "
                        + "The bot will now send step {} with price {} "
                        + "for marketplace listing {}.",
                decision.nextStep().getStepNumber(),
                decision.nextStep().getOfferPrice(),
                listing.listingId()
        );

        NextStepExecutionResult executionResult =
                nextNegotiationStepExecutor.sendNextStep(
                        listing,
                        decision.nextStep()
                );

        if (
                executionResult
                        == NextStepExecutionResult.SENT
        ) {

            log.warn(
                    "[NEXT STEP REAL] Step {} was sent successfully "
                            + "for marketplace listing {}. "
                            + "The backend was updated by "
                            + "NextNegotiationStepExecutor.",
                    decision.nextStep().getStepNumber(),
                    listing.listingId()
            );

            return true;
        }

        if (
                executionResult
                        == NextStepExecutionResult.OFFER_TOO_LOW
        ) {

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

    private ListingResponseDto markActionRequired(
            ListingResponseDto listing,
            NegotiationDecision decision
    ) {

        BigDecimal price =
                resolveDecisionPrice(
                        listing,
                        decision
                );

        ListingResponseDto updatedListing =
                updateNegotiationStatus(
                        listing,
                        "ACTION_REQUIRED",
                        price
                );

        if (
                !"ACTION_REQUIRED".equals(
                        updatedListing.status()
                )
        ) {

            throw new IllegalStateException(
                    "Backend returned an unexpected status. Expected "
                            + "ACTION_REQUIRED, actual: "
                            + updatedListing.status()
            );
        }

        return updatedListing;
    }

    private ListingResponseDto markRejected(
            ListingResponseDto listing,
            NegotiationDecision decision
    ) {

        BigDecimal price =
                resolveDecisionPrice(
                        listing,
                        decision
                );

        ListingResponseDto updatedListing =
                updateNegotiationStatus(
                        listing,
                        "REJECTED",
                        price
                );

        if (
                !"REJECTED".equals(
                        updatedListing.status()
                )
        ) {

            throw new IllegalStateException(
                    "Backend returned an unexpected status. Expected "
                            + "REJECTED, actual: "
                            + updatedListing.status()
            );
        }

        return updatedListing;
    }

    private ListingResponseDto updateNegotiationStatus(
            ListingResponseDto listing,
            String status,
            BigDecimal currentPrice
    ) {

        if (
                listing.currentStep() == null
                        || listing.currentStep() <= 0
        ) {

            throw new IllegalStateException(
                    "Cannot update negotiation status because backend "
                            + "listing "
                            + listing.id()
                            + " has an invalid current step: "
                            + listing.currentStep()
            );
        }

        UpdateListingRequestDto request =
                new UpdateListingRequestDto(
                        status,
                        currentPrice,
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

        if (
                Boolean.TRUE.equals(
                        updatedListing.awaitingSellerResponse()
                )
        ) {

            throw new IllegalStateException(
                    "Backend listing "
                            + listing.id()
                            + " still has awaitingSellerResponse=true "
                            + "after changing status to "
                            + status
            );
        }

        if (
                !Objects.equals(
                        listing.currentStep(),
                        updatedListing.currentStep()
                )
        ) {

            throw new IllegalStateException(
                    "Backend changed the current negotiation step "
                            + "unexpectedly. Expected: "
                            + listing.currentStep()
                            + ", actual: "
                            + updatedListing.currentStep()
            );
        }

        if (
                !Objects.equals(
                        listing.conversationId(),
                        updatedListing.conversationId()
                )
        ) {

            throw new IllegalStateException(
                    "Backend changed the conversation ID unexpectedly. "
                            + "Expected: "
                            + listing.conversationId()
                            + ", actual: "
                            + updatedListing.conversationId()
            );
        }

        if (
                updatedListing.currentPrice() == null
                        || updatedListing.currentPrice()
                        .compareTo(
                                currentPrice
                        ) != 0
        ) {

            throw new IllegalStateException(
                    "Backend returned an unexpected current price. "
                            + "Expected: "
                            + currentPrice
                            + ", actual: "
                            + updatedListing.currentPrice()
            );
        }

        return updatedListing;
    }

    private BigDecimal resolveDecisionPrice(
            ListingResponseDto listing,
            NegotiationDecision decision
    ) {

        /*
         * Przy zaakceptowanej kontrofertcie
         * sprzedającego zapisujemy cenę
         * sprzedającego.
         *
         * Dzięki temu frontend pokaże cenę,
         * za którą użytkownik może ręcznie
         * kupić przedmiot.
         */
        if (
                decision.sellerCounterOfferPrice()
                        != null
        ) {

            return decision
                    .sellerCounterOfferPrice();
        }

        /*
         * Gdy sprzedający zaakceptował
         * naszą ofertę, ceną zakupu
         * jest aktualna cena naszej
         * ostatniej propozycji.
         */
        if (listing.currentPrice() != null) {

            return listing.currentPrice();
        }

        if (listing.originalPrice() != null) {

            return listing.originalPrice();
        }

        throw new IllegalStateException(
                "Cannot determine decision price for backend listing "
                        + listing.id()
        );
    }

    private void markUnavailable(
            Long botId,
            ListingResponseDto listing
    ) {

        UpdateListingRequestDto request =
                createDiscoveredStatusUpdateRequest(
                        listing,
                        "UNAVAILABLE"
                );

        ListingResponseDto updatedListing =
                listingClient.updateListing(
                        botId,
                        listing.id(),
                        request
                );

        log.info(
                "Backend listing {} was marked as {}",
                updatedListing.id(),
                updatedListing.status()
        );
    }

    private void markOfferTooLow(
            Long botId,
            ListingResponseDto listing
    ) {

        UpdateListingRequestDto request =
                createDiscoveredStatusUpdateRequest(
                        listing,
                        "SKIPPED_OFFER_TOO_LOW"
                );

        ListingResponseDto updatedListing =
                listingClient.updateListing(
                        botId,
                        listing.id(),
                        request
                );

        log.info(
                "Backend listing {} was marked as {}",
                updatedListing.id(),
                updatedListing.status()
        );
    }

    private UpdateListingRequestDto
    createDiscoveredStatusUpdateRequest(
            ListingResponseDto listing,
            String status
    ) {

        BigDecimal currentPrice =
                listing.currentPrice() != null
                        ? listing.currentPrice()
                        : listing.originalPrice();

        if (currentPrice == null) {

            throw new IllegalStateException(
                    "Cannot update backend listing "
                            + listing.id()
                            + " because its price is null"
            );
        }

        Integer currentStep =
                listing.currentStep() != null
                        ? listing.currentStep()
                        : 0;

        return new UpdateListingRequestDto(
                status,
                currentPrice,
                currentStep,
                false,
                null,
                null
        );
    }
}