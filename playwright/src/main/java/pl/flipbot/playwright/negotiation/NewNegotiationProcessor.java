package pl.flipbot.playwright.negotiation;

import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.listing.ListingClient;
import pl.flipbot.playwright.api.listing.ListingStatusUpdater;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.api.listing.dto.UpdateListingRequestDto;
import pl.flipbot.playwright.api.quota.OfferQuotaClient;
import pl.flipbot.playwright.api.quota.dto.OfferQuotaReservationResponseDto;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.model.BotConfigurationDto;
import pl.flipbot.playwright.target.ListingDetailTargetInspector;
import pl.flipbot.playwright.target.ListingTargetAssessment;
import pl.flipbot.playwright.target.ListingTargetMatcher;
import pl.flipbot.playwright.target.ListingUnavailableDuringVerificationException;
import pl.flipbot.playwright.target.VintedRateLimitException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class NewNegotiationProcessor {

    private static final String VINTED_MODEL = "VINTED_MODEL";
    private static final int MAX_DETAIL_INSPECTIONS_PER_CYCLE = 5;

    /*
     * Historical DISCOVERED queues can contain stale or no-longer-matching
     * items. Checking only five final candidates meant that five bad backlog
     * entries could starve dozens of valid listings behind them. We now scan
     * further until enough candidates are verified for this run, with a
     * bounded ceiling to avoid excessive item-page traffic.
     */
    private static final int MAX_FINAL_VERIFICATIONS_PER_CYCLE = 20;

    private static final double DETAIL_INSPECTION_PACING_MS = 1_500;

    private final BotContext context;
    private final ListingClient listingClient;
    private final OfferQuotaClient offerQuotaClient;
    private final ListingStatusUpdater listingStatusUpdater;
    private final FirstOfferExecutor firstOfferExecutor;
    private final FirstOfferActionGuardCoordinator firstOfferActionGuardCoordinator;
    private final ListingTargetMatcher listingTargetMatcher;
    private final ListingDetailTargetInspector listingDetailTargetInspector;
    private final boolean realOffersEnabled;
    private final int maxRealOffersPerRun;

    public NewNegotiationProcessor(
            BotContext context,
            ListingClient listingClient,
            OfferQuotaClient offerQuotaClient,
            ListingStatusUpdater listingStatusUpdater,
            boolean realOffersEnabled,
            int maxRealOffersPerRun
    ) {
        this.context = context;
        this.listingClient = listingClient;
        this.offerQuotaClient = offerQuotaClient;
        this.listingStatusUpdater = listingStatusUpdater;
        this.firstOfferExecutor = new AdaptiveFirstOfferExecutor(context);
        this.firstOfferActionGuardCoordinator = new FirstOfferActionGuardCoordinator();
        this.listingTargetMatcher = new ListingTargetMatcher();
        this.listingDetailTargetInspector = new ListingDetailTargetInspector(
                context,
                listingTargetMatcher
        );
        this.realOffersEnabled = realOffersEnabled;
        this.maxRealOffersPerRun = maxRealOffersPerRun;
    }

    public void process(List<ListingResponseDto> priceEligibleListings) {
        if (priceEligibleListings == null || priceEligibleListings.isEmpty()) {
            log.info("[REAL OFFER] There are no price-eligible listings to process.");
            return;
        }

        BotConfigurationDto configuration = context.getBot().getConfiguration();
        if (configuration == null) {
            throw new IllegalStateException("Bot configuration is missing");
        }

        List<ListingResponseDto> targetEligibleListings = retainTargetEligibleListings(
                priceEligibleListings,
                configuration
        );

        if (targetEligibleListings.isEmpty()) {
            log.warn(
                    "[TARGET MATCHER] None of the {} price-eligible DISCOVERED candidates matches the configured target. No quota will be reserved and no negotiation will be started.",
                    priceEligibleListings.size()
            );
            return;
        }

        Long botId = context.getBot().getId();
        int allowedNewNegotiations = listingClient.getAllowedNewNegotiations(botId);

        if (allowedNewNegotiations <= 0) {
            log.info("[REAL OFFER] Bot {} cannot start any new negotiations.", botId);
            return;
        }

        if (!realOffersEnabled) {
            processDryRun(
                    targetEligibleListings,
                    configuration,
                    allowedNewNegotiations
            );
            return;
        }

        processRealOffers(
                targetEligibleListings,
                configuration,
                botId,
                allowedNewNegotiations
        );
    }

    private void processDryRun(
            List<ListingResponseDto> targetEligibleListings,
            BotConfigurationDto configuration,
            int allowedNewNegotiations
    ) {
        int desiredVerified = Math.min(
                targetEligibleListings.size(),
                allowedNewNegotiations
        );

        FinalVerificationResult finalVerification = verifyFinalCandidates(
                targetEligibleListings,
                configuration,
                MAX_FINAL_VERIFICATIONS_PER_CYCLE,
                desiredVerified
        );

        log.warn(
                "[REAL OFFER DRY RUN] Real offers are disabled. {} target-eligible DISCOVERED candidates were found. {} candidate(s) were checked by final target verification. {} passed, {} failed target verification, {} could not be verified. Backend allows {} new negotiations. No quota was reserved and no offer was sent.",
                targetEligibleListings.size(),
                finalVerification.checked(),
                finalVerification.verifiedListings().size(),
                finalVerification.mismatches(),
                finalVerification.failures(),
                allowedNewNegotiations
        );
    }

    private void processRealOffers(
            List<ListingResponseDto> targetEligibleListings,
            BotConfigurationDto configuration,
            Long botId,
            int allowedNewNegotiations
    ) {
        int maximumOffersThisRun = Math.min(
                allowedNewNegotiations,
                maxRealOffersPerRun
        );

        if (maximumOffersThisRun <= 0) {
            log.warn(
                    "[REAL OFFER] Real offers are enabled, but maxRealOffersPerRun={} prevents starting any offer.",
                    maxRealOffersPerRun
            );
            return;
        }

        log.warn(
                "[REAL OFFER] Real offers are enabled. Bot {} has {} target-eligible DISCOVERED candidates. Backend allows {} new negotiations. This run is limited to {} real offer(s). Final target verification will inspect up to {} candidates until {} verified candidate(s) are found. Quota is reserved only after the form is fully prepared and submit is ready.",
                botId,
                targetEligibleListings.size(),
                allowedNewNegotiations,
                maximumOffersThisRun,
                MAX_FINAL_VERIFICATIONS_PER_CYCLE,
                maximumOffersThisRun
        );

        FinalVerificationResult finalVerification = verifyFinalCandidates(
                targetEligibleListings,
                configuration,
                MAX_FINAL_VERIFICATIONS_PER_CYCLE,
                maximumOffersThisRun
        );

        List<ListingResponseDto> finalVerifiedListings =
                finalVerification.verifiedListings();

        if (finalVerifiedListings.isEmpty()) {
            log.warn(
                    "[REAL OFFER] No candidate passed mandatory final target verification. No quota will be reserved and no offer will be sent."
            );
            return;
        }

        log.info(
                "[REAL OFFER] {} candidate(s) passed mandatory final target verification. They may be tried in order until {} real negotiation(s) are started.",
                finalVerifiedListings.size(),
                maximumOffersThisRun
        );

        int startedNegotiations = 0;

        for (ListingResponseDto listing : finalVerifiedListings) {
            if (startedNegotiations >= maximumOffersThisRun) {
                break;
            }

            NegotiationPreparationResult preparationResult;

            try {
                preparationResult = firstOfferExecutor.prepareFirstOffer(listing);
            } catch (VintedRateLimitException exception) {
                throw exception;
            } catch (Exception exception) {
                log.error(
                        "[REAL OFFER PREPARE] Failed before quota reservation for marketplace listing {}: {}. No quota was reserved and no offer was sent. Trying the next verified candidate.",
                        listing.listingId(),
                        getFriendlyErrorMessage(exception)
                );
                log.trace(
                        "[REAL OFFER PREPARE] Full preparation error for marketplace listing {}.",
                        listing.listingId(),
                        exception
                );
                firstOfferExecutor.cancelPreparedOfferSafely();
                continue;
            }

            if (preparationResult == NegotiationPreparationResult.LISTING_UNAVAILABLE) {
                listingStatusUpdater.markUnavailable(botId, listing);
                continue;
            }

            if (preparationResult == NegotiationPreparationResult.TARGET_MISMATCH) {
                listingStatusUpdater.markTargetMismatch(botId, listing);
                continue;
            }

            if (preparationResult == NegotiationPreparationResult.OFFER_TOO_LOW) {
                listingStatusUpdater.markOfferTooLow(botId, listing);
                continue;
            }

            if (preparationResult == NegotiationPreparationResult.CANNOT_NEGOTIATE) {
                markCannotNegotiate(botId, listing);
                log.warn(
                        "[REAL OFFER] Skipping marketplace listing {} because Vinted exposes no negotiation action for this account. Backend status is now SKIPPED_CANNOT_NEGOTIATE. Trying the next fully verified candidate. No quota was reserved.",
                        listing.listingId()
                );
                continue;
            }

            if (preparationResult != NegotiationPreparationResult.PREPARED) {
                firstOfferExecutor.cancelPreparedOfferSafely();
                throw new IllegalStateException(
                        "Unexpected negotiation preparation result: " + preparationResult
                );
            }

            try {
                firstOfferExecutor.assertPreparedOfferReady(listing);
            } catch (Exception exception) {
                log.error(
                        "[REAL OFFER PREPARE] Prepared form became invalid before quota reservation for marketplace listing {}: {}. No quota was reserved. Trying the next verified candidate.",
                        listing.listingId(),
                        getFriendlyErrorMessage(exception)
                );
                log.trace(
                        "[REAL OFFER PREPARE] Full pre-quota readiness error for marketplace listing {}.",
                        listing.listingId(),
                        exception
                );
                firstOfferExecutor.cancelPreparedOfferSafely();
                continue;
            }

            var actionGuardRequestId =
                    firstOfferActionGuardCoordinator.acquire(
                            botId,
                            listing
                    );

            if (actionGuardRequestId == null) {
                firstOfferExecutor.cancelPreparedOfferSafely();
                log.error(
                        "[REAL OFFER] FIRST_OFFER action guard refused marketplace listing {}. Failing closed for this run; no quota was reserved and no real submit was attempted.",
                        listing.listingId()
                );
                return;
            }

            log.warn(
                    "[REAL OFFER] Marketplace listing {} passed every pre-submit guard. Persistent FIRST_OFFER guard is acquired. Reserving quota now, immediately before the real submit click.",
                    listing.listingId()
            );

            OfferQuotaReservationResponseDto quotaReservation;

            try {
                quotaReservation = offerQuotaClient.reserveSlot(botId);
            } catch (Exception exception) {
                firstOfferActionGuardCoordinator.releaseBeforeSubmitSafely(
                        botId,
                        listing,
                        actionGuardRequestId,
                        "quota reservation failed before real submit"
                );
                firstOfferExecutor.cancelPreparedOfferSafely();
                throw exception;
            }

            if (!quotaReservation.reserved()) {
                firstOfferActionGuardCoordinator.releaseBeforeSubmitSafely(
                        botId,
                        listing,
                        actionGuardRequestId,
                        "daily quota was not reserved"
                );

                log.warn(
                        "[REAL OFFER] Daily offer quota exhausted for bot {}. Used: {}/{}, remaining: {}. Prepared form will be closed and no offer will be sent.",
                        botId,
                        quotaReservation.used(),
                        quotaReservation.limit(),
                        quotaReservation.remaining()
                );

                firstOfferExecutor.cancelPreparedOfferSafely();
                return;
            }

            try {
                NegotiationStartResult result =
                        firstOfferExecutor.submitPreparedFirstNegotiation(listing);

                if (result != NegotiationStartResult.STARTED) {
                    throw new IllegalStateException(
                            "Unexpected negotiation start result after prepared submit: "
                                    + result
                    );
                }

                firstOfferActionGuardCoordinator.releaseAfterConfirmedSuccessBestEffort(
                        botId,
                        listing,
                        actionGuardRequestId
                );

                startedNegotiations++;

                log.warn(
                        "[REAL OFFER] Real negotiation STARTED for marketplace listing {}. Started during this run: {}/{}. Daily quota used: {}/{}, remaining: {}.",
                        listing.listingId(),
                        startedNegotiations,
                        maximumOffersThisRun,
                        quotaReservation.used(),
                        quotaReservation.limit(),
                        quotaReservation.remaining()
                );

            } catch (Exception exception) {
                log.error(
                        "[REAL OFFER] Failure occurred after quota reservation while submitting marketplace listing {}: {}. Quota will NOT be released automatically and FIRST_OFFER action guard will remain persisted because the real submit action may have been attempted.",
                        listing.listingId(),
                        getFriendlyErrorMessage(exception)
                );
                log.trace(
                        "[REAL OFFER] Full post-reservation submission error for marketplace listing {}.",
                        listing.listingId(),
                        exception
                );
                throw exception;
            }
        }

        log.info(
                "[REAL OFFER] Finished real-offer processing. Started {} negotiation(s).",
                startedNegotiations
        );
    }

    private void markCannotNegotiate(
            Long botId,
            ListingResponseDto listing
    ) {
        BigDecimal currentPrice = listing.currentPrice() != null
                ? listing.currentPrice()
                : listing.originalPrice();

        if (currentPrice == null) {
            throw new IllegalStateException(
                    "Cannot mark backend listing "
                            + listing.id()
                            + " as SKIPPED_CANNOT_NEGOTIATE because its price is null"
            );
        }

        Integer currentStep = listing.currentStep() != null
                ? listing.currentStep()
                : 0;

        UpdateListingRequestDto request = new UpdateListingRequestDto(
                "SKIPPED_CANNOT_NEGOTIATE",
                currentPrice,
                currentStep,
                false,
                null,
                null
        );

        ListingResponseDto updatedListing = listingClient.updateListing(
                botId,
                listing.id(),
                request
        );

        if (!"SKIPPED_CANNOT_NEGOTIATE".equals(updatedListing.status())) {
            throw new IllegalStateException(
                    "Backend returned an unexpected status after marking listing "
                            + listing.id()
                            + " as SKIPPED_CANNOT_NEGOTIATE. Actual: "
                            + updatedListing.status()
            );
        }

        log.info(
                "[CANNOT NEGOTIATE] Backend listing {} / marketplace listing {} was marked as SKIPPED_CANNOT_NEGOTIATE. No quota was reserved.",
                updatedListing.id(),
                listing.listingId()
        );
    }

    private FinalVerificationResult verifyFinalCandidates(
            List<ListingResponseDto> targetEligibleListings,
            BotConfigurationDto configuration,
            int maximumCandidatesToCheck,
            int desiredVerifiedCount
    ) {
        int candidatesToCheck = Math.min(
                targetEligibleListings.size(),
                maximumCandidatesToCheck
        );

        if (candidatesToCheck <= 0) {
            return new FinalVerificationResult(
                    List.of(),
                    0,
                    0,
                    0,
                    0
            );
        }

        log.info(
                "[FINAL VERIFY] Starting mandatory final target verification. Target-eligible={}, candidatesLimit={}, desiredVerified={}. No quota has been reserved.",
                targetEligibleListings.size(),
                candidatesToCheck,
                desiredVerifiedCount
        );

        List<ListingResponseDto> verifiedListings = new ArrayList<>();
        int checked = 0;
        int mismatches = 0;
        int failures = 0;
        int realItemPageRequests = 0;
        boolean exactVintedModelMode = usesExactVintedModelFilter(configuration);

        for (ListingResponseDto listing : targetEligibleListings) {
            if (checked >= candidatesToCheck) {
                break;
            }

            if (desiredVerifiedCount > 0
                    && verifiedListings.size() >= desiredVerifiedCount) {
                break;
            }

            checked++;

            boolean cached = !exactVintedModelMode
                    && listingDetailTargetInspector.hasCachedFullTitle(
                    listing.listingId()
            );
            boolean liveItemPageRequest = !exactVintedModelMode && !cached;

            if (liveItemPageRequest && realItemPageRequests > 0) {
                context.getPage().waitForTimeout(DETAIL_INSPECTION_PACING_MS);
            }

            if (liveItemPageRequest) {
                realItemPageRequests++;
            }

            String verificationSource = exactVintedModelMode
                    ? "EXACT_VINTED_MODEL_FILTER"
                    : cached
                    ? "FULL_TITLE_CACHE"
                    : "VINTED_ITEM_PAGE";

            log.info(
                    "[FINAL VERIFY] Candidate {}/{}. Backend listing={}, marketplace listing={}, catalog title='{}', price={}, targetMode={}, target='{}', source={}.",
                    checked,
                    candidatesToCheck,
                    listing.id(),
                    listing.listingId(),
                    listing.title(),
                    listing.originalPrice(),
                    configuration.getTargetMode(),
                    getConfiguredTargetLabel(configuration),
                    verificationSource
            );

            try {
                boolean matchesTarget =
                        listingDetailTargetInspector.matchesConfiguredTarget(
                                listing,
                                configuration
                        );

                if (matchesTarget) {
                    verifiedListings.add(listing);
                    log.info(
                            "[FINAL VERIFY] Marketplace listing {} PASSED mandatory final target verification. Verified candidates: {}/{}.",
                            listing.listingId(),
                            verifiedListings.size(),
                            desiredVerifiedCount
                    );
                } else {
                    mismatches++;
                    listingStatusUpdater.markTargetMismatch(
                            context.getBot().getId(),
                            listing
                    );
                    log.warn(
                            "[FINAL VERIFY] Marketplace listing {} FAILED mandatory final target verification and was persisted as SKIPPED_TARGET_MISMATCH. It will not block later backlog candidates.",
                            listing.listingId()
                    );
                }

            } catch (VintedRateLimitException exception) {
                log.warn(
                        "[RATE LIMIT] Vinted rate limit detected during mandatory final verification of marketplace listing {}. Stopping this work cycle.",
                        listing.listingId()
                );
                throw exception;

            } catch (ListingUnavailableDuringVerificationException exception) {
                listingStatusUpdater.markUnavailable(
                        context.getBot().getId(),
                        listing
                );
                log.info(
                        "[FINAL VERIFY] Marketplace listing {} became unavailable and was persisted as UNAVAILABLE. Verification continues with the next candidate.",
                        listing.listingId()
                );

            } catch (Exception exception) {
                failures++;
                log.warn(
                        "[FINAL VERIFY] Could not verify marketplace listing {}: {}. It remains DISCOVERED and will be retried in a later cycle; verification will continue with the next candidate now.",
                        listing.listingId(),
                        getFriendlyErrorMessage(exception)
                );
                log.trace(
                        "[FINAL VERIFY] Full verification error for marketplace listing {}.",
                        listing.listingId(),
                        exception
                );
            }
        }

        log.info(
                "[FINAL VERIFY] Finished. Checked={}, passed={}, mismatches={}, failures={}, real item-page requests={}.",
                checked,
                verifiedListings.size(),
                mismatches,
                failures,
                realItemPageRequests
        );

        return new FinalVerificationResult(
                List.copyOf(verifiedListings),
                checked,
                mismatches,
                failures,
                realItemPageRequests
        );
    }

    private List<ListingResponseDto> retainTargetEligibleListings(
            List<ListingResponseDto> listings,
            BotConfigurationDto configuration
    ) {
        List<ListingResponseDto> eligibleListings = new ArrayList<>();

        int matchedFromCatalogTitle = 0;
        int matchedFromUrlSlug = 0;
        int matchedFromDetailCache = 0;
        int matchedAfterDetailRequest = 0;
        int rejectedCatalogMismatch = 0;
        int rejectedUrlMismatch = 0;
        int rejectedFromDetailCache = 0;
        int rejectedAfterDetailRequest = 0;
        int detailInspectionFailures = 0;
        int deferredByDetailLimit = 0;
        int detailRequestsThisCycle = 0;
        int persistedTargetMismatches = 0;
        int persistedUnavailable = 0;
        Long botId = context.getBot().getId();

        for (ListingResponseDto listing : listings) {
            ListingTargetAssessment catalogAssessment =
                    listingTargetMatcher.assessCatalogListing(
                            listing,
                            configuration
                    );

            if (catalogAssessment == ListingTargetAssessment.MATCH) {
                eligibleListings.add(listing);
                matchedFromCatalogTitle++;
                continue;
            }

            if (catalogAssessment == ListingTargetAssessment.MISMATCH) {
                rejectedCatalogMismatch++;
                listingStatusUpdater.markTargetMismatch(botId, listing);
                persistedTargetMismatches++;
                continue;
            }

            ListingTargetAssessment urlAssessment =
                    listingTargetMatcher.assessListingUrl(
                            listing,
                            configuration
                    );

            if (urlAssessment == ListingTargetAssessment.MATCH) {
                eligibleListings.add(listing);
                matchedFromUrlSlug++;
                continue;
            }

            if (urlAssessment == ListingTargetAssessment.MISMATCH) {
                rejectedUrlMismatch++;
                listingStatusUpdater.markTargetMismatch(botId, listing);
                persistedTargetMismatches++;
                continue;
            }

            boolean cached = listingDetailTargetInspector.hasCachedFullTitle(
                    listing.listingId()
            );

            if (cached) {
                boolean cachedMatches =
                        listingDetailTargetInspector.matchesConfiguredTarget(
                                listing,
                                configuration
                        );

                if (cachedMatches) {
                    eligibleListings.add(listing);
                    matchedFromDetailCache++;
                } else {
                    rejectedFromDetailCache++;
                    listingStatusUpdater.markTargetMismatch(botId, listing);
                    persistedTargetMismatches++;
                }

                continue;
            }

            if (detailRequestsThisCycle >= MAX_DETAIL_INSPECTIONS_PER_CYCLE) {
                deferredByDetailLimit++;
                log.info(
                        "[TARGET DETAIL] Marketplace listing {} is still ambiguous, but the per-cycle detail limit ({}) has already been reached. The listing is deferred safely to a later cycle before quota reservation.",
                        listing.listingId(),
                        MAX_DETAIL_INSPECTIONS_PER_CYCLE
                );
                continue;
            }

            if (detailRequestsThisCycle > 0) {
                context.getPage().waitForTimeout(DETAIL_INSPECTION_PACING_MS);
            }

            detailRequestsThisCycle++;

            try {
                boolean detailMatches =
                        listingDetailTargetInspector.matchesConfiguredTarget(
                                listing,
                                configuration
                        );

                if (detailMatches) {
                    eligibleListings.add(listing);
                    matchedAfterDetailRequest++;
                } else {
                    rejectedAfterDetailRequest++;
                    listingStatusUpdater.markTargetMismatch(botId, listing);
                    persistedTargetMismatches++;
                }

            } catch (VintedRateLimitException exception) {
                log.warn(
                        "[RATE LIMIT] Vinted rate limit detected while inspecting marketplace listing {}. Stopping this work cycle immediately.",
                        listing.listingId()
                );
                throw exception;

            } catch (ListingUnavailableDuringVerificationException exception) {
                listingStatusUpdater.markUnavailable(botId, listing);
                persistedUnavailable++;
                log.info(
                        "[TARGET DETAIL] Marketplace listing {} became unavailable during target inspection and was persisted as UNAVAILABLE.",
                        listing.listingId()
                );

            } catch (Exception exception) {
                detailInspectionFailures++;
                log.warn(
                        "[TARGET DETAIL] Failed to inspect marketplace listing {}. It remains DISCOVERED and will be retried in a later cycle. Error: {}",
                        listing.listingId(),
                        getFriendlyErrorMessage(exception)
                );
                log.trace(
                        "[TARGET DETAIL] Full inspection error for marketplace listing {}.",
                        listing.listingId(),
                        exception
                );
            }
        }

        log.info(
                "[TARGET MATCHER] Checked {} price-eligible DISCOVERED candidates. Catalog matches: {}, URL matches: {}, detail-cache matches: {}, detail-request matches: {}, catalog mismatches: {}, URL mismatches: {}, detail-cache mismatches: {}, detail-request mismatches: {}, detail requests this cycle: {}/{}, detail failures: {}, deferred by detail limit: {}, persisted target mismatches: {}, persisted unavailable: {}, final eligible: {}. Target mode: {}.",
                listings.size(),
                matchedFromCatalogTitle,
                matchedFromUrlSlug,
                matchedFromDetailCache,
                matchedAfterDetailRequest,
                rejectedCatalogMismatch,
                rejectedUrlMismatch,
                rejectedFromDetailCache,
                rejectedAfterDetailRequest,
                detailRequestsThisCycle,
                MAX_DETAIL_INSPECTIONS_PER_CYCLE,
                detailInspectionFailures,
                deferredByDetailLimit,
                persistedTargetMismatches,
                persistedUnavailable,
                eligibleListings.size(),
                configuration.getTargetMode()
        );

        return eligibleListings;
    }

    private boolean usesExactVintedModelFilter(
            BotConfigurationDto configuration
    ) {
        return configuration != null
                && VINTED_MODEL.equalsIgnoreCase(configuration.getTargetMode());
    }

    private String getConfiguredTargetLabel(
            BotConfigurationDto configuration
    ) {
        if (configuration == null) {
            return "null";
        }

        if ("SEARCH_QUERY".equalsIgnoreCase(configuration.getTargetMode())) {
            return configuration.getSearchQuery();
        }

        if (VINTED_MODEL.equalsIgnoreCase(configuration.getTargetMode())) {
            return configuration.getModel();
        }

        return "unknown";
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

    private record FinalVerificationResult(
            List<ListingResponseDto> verifiedListings,
            int checked,
            int mismatches,
            int failures,
            int realItemPageRequests
    ) {
    }
}
