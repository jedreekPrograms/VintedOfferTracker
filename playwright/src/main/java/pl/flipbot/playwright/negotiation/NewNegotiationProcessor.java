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
import pl.flipbot.playwright.target.VintedRateLimitException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class NewNegotiationProcessor {

    private static final int MAX_DETAIL_INSPECTIONS_PER_CYCLE =
            5;

    private static final int MAX_FINAL_VERIFICATIONS_PER_CYCLE =
            5;

    private static final double DETAIL_INSPECTION_PACING_MS =
            1_500;


    private final BotContext context;

    private final ListingClient listingClient;

    private final OfferQuotaClient offerQuotaClient;

    private final ListingStatusUpdater listingStatusUpdater;

    private final FirstOfferExecutor firstOfferExecutor;

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

        this.context =
                context;

        this.listingClient =
                listingClient;

        this.offerQuotaClient =
                offerQuotaClient;

        this.listingStatusUpdater =
                listingStatusUpdater;

        this.firstOfferExecutor =
                new FirstOfferExecutor(
                        context
                );

        this.listingTargetMatcher =
                new ListingTargetMatcher();

        this.listingDetailTargetInspector =
                new ListingDetailTargetInspector(
                        context,
                        listingTargetMatcher
                );

        this.realOffersEnabled =
                realOffersEnabled;

        this.maxRealOffersPerRun =
                maxRealOffersPerRun;
    }


    public void process(
            List<ListingResponseDto> priceEligibleListings
    ) {

        if (
                priceEligibleListings == null
                        || priceEligibleListings.isEmpty()
        ) {

            log.info(
                    "[REAL OFFER] There are no price-eligible listings "
                            + "to process."
            );

            return;
        }

        BotConfigurationDto configuration =
                context.getBot()
                        .getConfiguration();

        if (
                configuration == null
        ) {

            throw new IllegalStateException(
                    "Bot configuration is missing"
            );
        }

        List<ListingResponseDto> targetEligibleListings =
                retainTargetEligibleListings(
                        priceEligibleListings,
                        configuration
                );

        if (
                targetEligibleListings.isEmpty()
        ) {

            log.warn(
                    "[TARGET MATCHER] None of the {} price-eligible "
                            + "current-scan listings matches the configured "
                            + "target. No quota will be reserved and no "
                            + "negotiation will be started.",
                    priceEligibleListings.size()
            );

            return;
        }

        Long botId =
                context.getBot()
                        .getId();

        int allowedNewNegotiations =
                listingClient.getAllowedNewNegotiations(
                        botId
                );

        if (
                allowedNewNegotiations <= 0
        ) {

            log.info(
                    "[REAL OFFER] Bot {} cannot start any new negotiations.",
                    botId
            );

            return;
        }

        if (
                !realOffersEnabled
        ) {

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

        int maximumCandidatesToVerify =
                Math.min(
                        targetEligibleListings.size(),
                        allowedNewNegotiations
                );

        maximumCandidatesToVerify =
                Math.min(
                        maximumCandidatesToVerify,
                        MAX_FINAL_VERIFICATIONS_PER_CYCLE
                );

        FinalVerificationResult finalVerification =
                verifyFinalCandidates(
                        targetEligibleListings,
                        configuration,
                        maximumCandidatesToVerify,
                        maximumCandidatesToVerify
                );

        log.warn(
                "[REAL OFFER DRY RUN] Real offers are disabled. "
                        + "{} target-eligible current-scan listings were found. "
                        + "{} candidate(s) were checked by final full-title "
                        + "verification. {} passed, {} failed target "
                        + "verification, {} could not be verified. "
                        + "Backend allows {} new negotiations. "
                        + "No quota was reserved and no offer was sent.",
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

        int maximumOffersThisRun =
                Math.min(
                        allowedNewNegotiations,
                        maxRealOffersPerRun
                );

        if (
                maximumOffersThisRun <= 0
        ) {

            log.warn(
                    "[REAL OFFER] Real offers are enabled, but "
                            + "maxRealOffersPerRun={} prevents starting any "
                            + "offer.",
                    maxRealOffersPerRun
            );

            return;
        }

        log.warn(
                "[REAL OFFER] Real offers are enabled. Bot {} has {} "
                        + "target-eligible current-scan listings. Backend "
                        + "allows {} new negotiations. This run is limited "
                        + "to {} real offer(s). Final full-title verification "
                        + "will run before offer-form preparation, and quota "
                        + "will be reserved only after the form is fully "
                        + "prepared and the submit button is ready.",
                botId,
                targetEligibleListings.size(),
                allowedNewNegotiations,
                maximumOffersThisRun
        );

        /*
         * W real mode weryfikujemy także kandydatów zapasowych.
         * Nadal możemy wysłać maksymalnie maximumOffersThisRun ofert,
         * ale pierwszy poprawny listing może nie pozwalać temu kontu
         * rozpocząć negocjacji.
         */
        FinalVerificationResult finalVerification =
                verifyFinalCandidates(
                        targetEligibleListings,
                        configuration,
                        MAX_FINAL_VERIFICATIONS_PER_CYCLE,
                        MAX_FINAL_VERIFICATIONS_PER_CYCLE
                );

        List<ListingResponseDto> finalVerifiedListings =
                finalVerification.verifiedListings();

        if (
                finalVerifiedListings.isEmpty()
        ) {

            log.warn(
                    "[REAL OFFER] No candidate passed mandatory final "
                            + "full-title verification. No quota will be "
                            + "reserved and no offer will be sent."
            );

            return;
        }

        log.info(
                "[REAL OFFER] {} candidate(s) passed mandatory final "
                        + "full-title verification. They may be tried in "
                        + "order until {} real negotiation(s) are started.",
                finalVerifiedListings.size(),
                maximumOffersThisRun
        );

        int startedNegotiations =
                0;

        for (
                ListingResponseDto listing
                : finalVerifiedListings
        ) {

            if (
                    startedNegotiations
                            >= maximumOffersThisRun
            ) {

                break;
            }

            /*
             * ========================================================
             * PREPARE BEFORE QUOTA
             * ========================================================
             *
             * Tutaj:
             * - strona ogłoszenia musi być poprawnie załadowana,
             * - przycisk "Zaproponuj cenę" musi istnieć,
             * - formularz musi się otworzyć,
             * - cena musi zostać wpisana,
             * - walidacja Vinted musi przejść,
             * - submit musi być widoczny i aktywny.
             *
             * Dopiero PO tym rezerwujemy quota.
             */
            NegotiationPreparationResult preparationResult;

            try {

                preparationResult =
                        firstOfferExecutor.prepareFirstOffer(
                                listing
                        );

            } catch (VintedRateLimitException exception) {

                throw exception;

            } catch (Exception exception) {

                log.error(
                        "[REAL OFFER PREPARE] Failed before quota reservation "
                                + "for marketplace listing {}: {}. "
                                + "No quota was reserved and no offer was sent.",
                        listing.listingId(),
                        getFriendlyErrorMessage(
                                exception
                        )
                );

                log.trace(
                        "[REAL OFFER PREPARE] Full preparation error for "
                                + "marketplace listing {}.",
                        listing.listingId(),
                        exception
                );

                firstOfferExecutor.cancelPreparedOfferSafely();

                return;
            }

            if (
                    preparationResult
                            == NegotiationPreparationResult.LISTING_UNAVAILABLE
            ) {

                listingStatusUpdater.markUnavailable(
                        botId,
                        listing
                );

                continue;
            }

            if (
                    preparationResult
                            == NegotiationPreparationResult.OFFER_TOO_LOW
            ) {

                listingStatusUpdater.markOfferTooLow(
                        botId,
                        listing
                );

                continue;
            }

            if (
                    preparationResult
                            == NegotiationPreparationResult.CANNOT_NEGOTIATE
            ) {

                markCannotNegotiate(
                        botId,
                        listing
                );

                log.warn(
                        "[REAL OFFER] Skipping marketplace listing {} because "
                                + "Vinted exposes no negotiation action for "
                                + "this account. Backend status is now "
                                + "SKIPPED_CANNOT_NEGOTIATE. Trying the next "
                                + "fully verified candidate. No quota was "
                                + "reserved.",
                        listing.listingId()
                );

                continue;
            }

            if (
                    preparationResult
                            != NegotiationPreparationResult.PREPARED
            ) {

                firstOfferExecutor.cancelPreparedOfferSafely();

                throw new IllegalStateException(
                        "Unexpected negotiation preparation result: "
                                + preparationResult
                );
            }

            /*
             * Ostatni check nadal odbywa się PRZED reserveSlot().
             */
            try {

                firstOfferExecutor.assertPreparedOfferReady(
                        listing
                );

            } catch (Exception exception) {

                log.error(
                        "[REAL OFFER PREPARE] Prepared form became invalid "
                                + "before quota reservation for marketplace "
                                + "listing {}: {}. No quota was reserved.",
                        listing.listingId(),
                        getFriendlyErrorMessage(
                                exception
                        )
                );

                log.trace(
                        "[REAL OFFER PREPARE] Full pre-quota readiness error "
                                + "for marketplace listing {}.",
                        listing.listingId(),
                        exception
                );

                firstOfferExecutor.cancelPreparedOfferSafely();

                return;
            }

            log.warn(
                    "[REAL OFFER] Marketplace listing {} passed every "
                            + "pre-submit guard. Reserving quota now, "
                            + "immediately before the real submit click.",
                    listing.listingId()
            );

            OfferQuotaReservationResponseDto quotaReservation =
                    offerQuotaClient.reserveSlot(
                            botId
                    );

            if (
                    !quotaReservation.reserved()
            ) {

                log.warn(
                        "[REAL OFFER] Daily offer quota exhausted for bot {}. "
                                + "Used: {}/{}, remaining: {}. Prepared form "
                                + "will be closed and no offer will be sent.",
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
                        firstOfferExecutor
                                .submitPreparedFirstNegotiation(
                                        listing
                                );

                if (
                        result
                                != NegotiationStartResult.STARTED
                ) {

                    throw new IllegalStateException(
                            "Unexpected negotiation start result after "
                                    + "prepared submit: "
                                    + result
                    );
                }

                startedNegotiations++;

                log.warn(
                        "[REAL OFFER] Real negotiation STARTED for "
                                + "marketplace listing {}. Started during "
                                + "this run: {}. Daily quota used: {}/{}, "
                                + "remaining: {}.",
                        listing.listingId(),
                        startedNegotiations,
                        quotaReservation.used(),
                        quotaReservation.limit(),
                        quotaReservation.remaining()
                );

                /*
                 * Zachowujemy maksymalnie konserwatywne zachowanie:
                 * po jednej skutecznie wysłanej realnej ofercie kończymy run.
                 */
                return;

            } catch (Exception exception) {

                /*
                 * Quota jest już zarezerwowana i weszliśmy do metody,
                 * której pierwszą operacją jest realny click submit.
                 *
                 * Jeżeli cokolwiek tutaj zawiedzie, stan dostarczenia może być
                 * niejednoznaczny. NIE zwalniamy quota automatycznie.
                 */
                log.error(
                        "[REAL OFFER] Failure occurred after quota reservation "
                                + "while submitting marketplace listing {}: {}. "
                                + "Quota will NOT be released automatically "
                                + "because the real submit action may have been "
                                + "attempted.",
                        listing.listingId(),
                        getFriendlyErrorMessage(
                                exception
                        )
                );

                log.trace(
                        "[REAL OFFER] Full post-reservation submission error "
                                + "for marketplace listing {}.",
                        listing.listingId(),
                        exception
                );

                throw exception;
            }
        }

        log.info(
                "[REAL OFFER] Finished real-offer processing. "
                        + "Started {} negotiation(s).",
                startedNegotiations
        );
    }


    private void markCannotNegotiate(
            Long botId,
            ListingResponseDto listing
    ) {

        BigDecimal currentPrice =
                listing.currentPrice() != null
                        ? listing.currentPrice()
                        : listing.originalPrice();

        if (
                currentPrice == null
        ) {

            throw new IllegalStateException(
                    "Cannot mark backend listing "
                            + listing.id()
                            + " as SKIPPED_CANNOT_NEGOTIATE because its "
                            + "price is null"
            );
        }

        Integer currentStep =
                listing.currentStep() != null
                        ? listing.currentStep()
                        : 0;

        UpdateListingRequestDto request =
                new UpdateListingRequestDto(
                        "SKIPPED_CANNOT_NEGOTIATE",
                        currentPrice,
                        currentStep,
                        false,
                        null,
                        null
                );

        ListingResponseDto updatedListing =
                listingClient.updateListing(
                        botId,
                        listing.id(),
                        request
                );

        if (
                !"SKIPPED_CANNOT_NEGOTIATE".equals(
                        updatedListing.status()
                )
        ) {

            throw new IllegalStateException(
                    "Backend returned an unexpected status after marking "
                            + "listing "
                            + listing.id()
                            + " as SKIPPED_CANNOT_NEGOTIATE. Actual: "
                            + updatedListing.status()
            );
        }

        log.info(
                "[CANNOT NEGOTIATE] Backend listing {} / marketplace listing "
                        + "{} was marked as SKIPPED_CANNOT_NEGOTIATE. "
                        + "No quota was reserved.",
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

        int candidatesToCheck =
                Math.min(
                        targetEligibleListings.size(),
                        maximumCandidatesToCheck
                );

        candidatesToCheck =
                Math.min(
                        candidatesToCheck,
                        MAX_FINAL_VERIFICATIONS_PER_CYCLE
                );

        if (
                candidatesToCheck <= 0
        ) {

            return new FinalVerificationResult(
                    List.of(),
                    0,
                    0,
                    0,
                    0
            );
        }

        log.info(
                "[FINAL VERIFY] Starting mandatory full-title verification. "
                        + "Target-eligible={}, candidatesLimit={}, "
                        + "desiredVerified={}. No quota has been reserved.",
                targetEligibleListings.size(),
                candidatesToCheck,
                desiredVerifiedCount
        );

        List<ListingResponseDto> verifiedListings =
                new ArrayList<>();

        int checked =
                0;

        int mismatches =
                0;

        int failures =
                0;

        int realItemPageRequests =
                0;

        for (
                ListingResponseDto listing
                : targetEligibleListings
        ) {

            if (
                    checked >= candidatesToCheck
            ) {

                break;
            }

            if (
                    desiredVerifiedCount > 0
                            && verifiedListings.size()
                            >= desiredVerifiedCount
            ) {

                break;
            }

            checked++;

            boolean cached =
                    listingDetailTargetInspector
                            .hasCachedFullTitle(
                                    listing.listingId()
                            );

            if (
                    !cached
                            && realItemPageRequests > 0
            ) {

                context.getPage()
                        .waitForTimeout(
                                DETAIL_INSPECTION_PACING_MS
                        );
            }

            if (
                    !cached
            ) {

                realItemPageRequests++;
            }

            log.info(
                    "[FINAL VERIFY] Candidate {}/{}. Backend listing={}, "
                            + "marketplace listing={}, catalog title='{}', "
                            + "price={}, targetMode={}, target='{}', source={}.",
                    checked,
                    candidatesToCheck,
                    listing.id(),
                    listing.listingId(),
                    listing.title(),
                    listing.originalPrice(),
                    configuration.getTargetMode(),
                    getConfiguredTargetLabel(
                            configuration
                    ),
                    cached
                            ? "FULL_TITLE_CACHE"
                            : "VINTED_ITEM_PAGE"
            );

            try {

                boolean matchesTarget =
                        listingDetailTargetInspector
                                .matchesConfiguredTarget(
                                        listing,
                                        configuration
                                );

                if (
                        matchesTarget
                ) {

                    verifiedListings.add(
                            listing
                    );

                    log.info(
                            "[FINAL VERIFY] Marketplace listing {} PASSED "
                                    + "mandatory full-title verification. "
                                    + "Verified candidates: {}/{}.",
                            listing.listingId(),
                            verifiedListings.size(),
                            desiredVerifiedCount
                    );

                } else {

                    mismatches++;

                    log.warn(
                            "[FINAL VERIFY] Marketplace listing {} FAILED "
                                    + "mandatory full-title verification. It "
                                    + "will NOT proceed toward quota "
                                    + "reservation.",
                            listing.listingId()
                    );
                }

            } catch (VintedRateLimitException exception) {

                log.warn(
                        "[RATE LIMIT] Vinted rate limit detected during "
                                + "mandatory final verification of marketplace "
                                + "listing {}. Stopping this work cycle.",
                        listing.listingId()
                );

                throw exception;

            } catch (Exception exception) {

                failures++;

                log.warn(
                        "[FINAL VERIFY] Could not verify marketplace listing "
                                + "{}: {}. The listing will NOT proceed toward "
                                + "quota reservation.",
                        listing.listingId(),
                        getFriendlyErrorMessage(
                                exception
                        )
                );

                log.trace(
                        "[FINAL VERIFY] Full verification error for "
                                + "marketplace listing {}.",
                        listing.listingId(),
                        exception
                );
            }
        }

        log.info(
                "[FINAL VERIFY] Finished. Checked={}, passed={}, "
                        + "mismatches={}, failures={}, real item-page "
                        + "requests={}. No quota was reserved during "
                        + "verification.",
                checked,
                verifiedListings.size(),
                mismatches,
                failures,
                realItemPageRequests
        );

        return new FinalVerificationResult(
                List.copyOf(
                        verifiedListings
                ),
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

        List<ListingResponseDto> eligibleListings =
                new ArrayList<>();

        int matchedFromCatalogTitle =
                0;

        int matchedFromUrlSlug =
                0;

        int matchedFromDetailCache =
                0;

        int matchedAfterDetailRequest =
                0;

        int rejectedCatalogMismatch =
                0;

        int rejectedUrlMismatch =
                0;

        int rejectedFromDetailCache =
                0;

        int rejectedAfterDetailRequest =
                0;

        int detailInspectionFailures =
                0;

        int deferredByDetailLimit =
                0;

        int detailRequestsThisCycle =
                0;

        for (
                ListingResponseDto listing
                : listings
        ) {

            ListingTargetAssessment catalogAssessment =
                    listingTargetMatcher
                            .assessCatalogListing(
                                    listing,
                                    configuration
                            );

            if (
                    catalogAssessment
                            == ListingTargetAssessment.MATCH
            ) {

                eligibleListings.add(
                        listing
                );

                matchedFromCatalogTitle++;

                continue;
            }

            if (
                    catalogAssessment
                            == ListingTargetAssessment.MISMATCH
            ) {

                rejectedCatalogMismatch++;

                continue;
            }

            ListingTargetAssessment urlAssessment =
                    listingTargetMatcher
                            .assessListingUrl(
                                    listing,
                                    configuration
                            );

            if (
                    urlAssessment
                            == ListingTargetAssessment.MATCH
            ) {

                eligibleListings.add(
                        listing
                );

                matchedFromUrlSlug++;

                continue;
            }

            if (
                    urlAssessment
                            == ListingTargetAssessment.MISMATCH
            ) {

                rejectedUrlMismatch++;

                continue;
            }

            boolean cached =
                    listingDetailTargetInspector
                            .hasCachedFullTitle(
                                    listing.listingId()
                            );

            if (
                    cached
            ) {

                boolean cachedMatches =
                        listingDetailTargetInspector
                                .matchesConfiguredTarget(
                                        listing,
                                        configuration
                                );

                if (
                        cachedMatches
                ) {

                    eligibleListings.add(
                            listing
                    );

                    matchedFromDetailCache++;

                } else {

                    rejectedFromDetailCache++;
                }

                continue;
            }

            if (
                    detailRequestsThisCycle
                            >= MAX_DETAIL_INSPECTIONS_PER_CYCLE
            ) {

                deferredByDetailLimit++;

                log.info(
                        "[TARGET DETAIL] Marketplace listing {} is still "
                                + "ambiguous, but the per-cycle detail limit "
                                + "({}) has already been reached. The listing "
                                + "is deferred safely to a later cycle before "
                                + "quota reservation.",
                        listing.listingId(),
                        MAX_DETAIL_INSPECTIONS_PER_CYCLE
                );

                continue;
            }

            if (
                    detailRequestsThisCycle > 0
            ) {

                context.getPage()
                        .waitForTimeout(
                                DETAIL_INSPECTION_PACING_MS
                        );
            }

            detailRequestsThisCycle++;

            try {

                boolean detailMatches =
                        listingDetailTargetInspector
                                .matchesConfiguredTarget(
                                        listing,
                                        configuration
                                );

                if (
                        detailMatches
                ) {

                    eligibleListings.add(
                            listing
                    );

                    matchedAfterDetailRequest++;

                } else {

                    rejectedAfterDetailRequest++;
                }

            } catch (VintedRateLimitException exception) {

                log.warn(
                        "[RATE LIMIT] Vinted rate limit detected while "
                                + "inspecting marketplace listing {}. "
                                + "Stopping this work cycle immediately.",
                        listing.listingId()
                );

                throw exception;

            } catch (Exception exception) {

                detailInspectionFailures++;

                log.warn(
                        "[TARGET DETAIL] Failed to inspect marketplace "
                                + "listing {}. It will be skipped for this "
                                + "cycle before quota reservation. Error: {}",
                        listing.listingId(),
                        getFriendlyErrorMessage(
                                exception
                        )
                );

                log.trace(
                        "[TARGET DETAIL] Full inspection error for "
                                + "marketplace listing {}.",
                        listing.listingId(),
                        exception
                );
            }
        }

        log.info(
                "[TARGET MATCHER] Checked {} current-scan price-eligible "
                        + "listings. Catalog matches: {}, URL matches: {}, "
                        + "detail-cache matches: {}, detail-request matches: {}, "
                        + "catalog mismatches: {}, URL mismatches: {}, "
                        + "detail-cache mismatches: {}, detail-request "
                        + "mismatches: {}, detail requests this cycle: {}/{}, "
                        + "detail failures: {}, deferred by detail limit: {}, "
                        + "final eligible: {}. Target mode: {}.",
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
                eligibleListings.size(),
                configuration.getTargetMode()
        );

        return eligibleListings;
    }


    private String getConfiguredTargetLabel(
            BotConfigurationDto configuration
    ) {

        if (
                configuration == null
        ) {

            return "null";
        }

        if (
                "SEARCH_QUERY".equalsIgnoreCase(
                        configuration.getTargetMode()
                )
        ) {

            return configuration.getSearchQuery();
        }

        if (
                "VINTED_MODEL".equalsIgnoreCase(
                        configuration.getTargetMode()
                )
        ) {

            return configuration.getModel();
        }

        return "unknown";
    }


    private String getFriendlyErrorMessage(
            Throwable exception
    ) {

        if (
                exception == null
        ) {

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
                message.indexOf(
                        '\n'
                );

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


    private record FinalVerificationResult(
            List<ListingResponseDto> verifiedListings,
            int checked,
            int mismatches,
            int failures,
            int realItemPageRequests
    ) {
    }
}