package pl.flipbot.playwright.negotiation;

import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.listing.ListingClient;
import pl.flipbot.playwright.api.listing.ListingStatusUpdater;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.api.quota.OfferQuotaClient;
import pl.flipbot.playwright.api.quota.dto.OfferQuotaReservationResponseDto;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.model.BotConfigurationDto;
import pl.flipbot.playwright.target.ListingDetailTargetInspector;
import pl.flipbot.playwright.target.ListingTargetAssessment;
import pl.flipbot.playwright.target.ListingTargetMatcher;
import pl.flipbot.playwright.target.VintedRateLimitException;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class NewNegotiationProcessor {

    /*
     * Nawet gdy wiele listingów ma niepełny tytuł i URL,
     * nie otwieramy dziesiątek item pages w jednym cyklu.
     */
    private static final int MAX_DETAIL_INSPECTIONS_PER_CYCLE =
            5;

    /*
     * Delikatne tempo pomiędzy faktycznymi wejściami na /items/...
     * Nie dotyczy cache ani analizy sluga URL.
     */
    private static final double DETAIL_INSPECTION_PACING_MS =
            1_500;


    private final BotContext context;

    private final ListingClient listingClient;

    private final OfferQuotaClient offerQuotaClient;

    private final ListingStatusUpdater
            listingStatusUpdater;

    private final NegotiationExecutor
            negotiationExecutor;

    private final ListingTargetMatcher
            listingTargetMatcher;

    private final ListingDetailTargetInspector
            listingDetailTargetInspector;

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

        this.negotiationExecutor =
                new NegotiationExecutor(
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


        if (configuration == null) {

            throw new IllegalStateException(
                    "Bot configuration is missing"
            );
        }


        /*
         * FINAL TARGET GUARD.
         *
         * Działa niezależnie od filtrów Vinted i PRZED reserveSlot().
         *
         * VINTED_MODEL:
         * - np. Galaxy S25 -> wymagamy charakterystycznych tokenów modelu,
         *   np. s25;
         * - Galaxy S25+ pozostaje różne od Galaxy S25.
         *
         * SEARCH_QUERY:
         * - np. Galaxy Tab S11 Ultra -> wymagamy wszystkich istotnych
         *   tokenów: tab, s11, ultra;
         * - wielkość liter, myślniki, podkreślenia, zapis "S 11"
         *   oraz złączone warianty typu S11Ultra nie mają znaczenia;
         * - najpierw używamy tytułu karty;
         * - potem BEZ requestu analizujemy slug URL;
         * - dopiero gdy oba źródła są niejednoznaczne, możemy wejść
         *   na stronę przedmiotu i przeczytać pełny <h1>;
         * - pełne tytuły są cache'owane, a liczba wejść /items/...
         *   w jednym cyklu jest ograniczona.
         *
         * Listing, który nie przejdzie guarda:
         * - nie rezerwuje quota,
         * - nie trafia do NegotiationExecutor,
         * - nie może wysłać oferty.
         */
        List<ListingResponseDto> targetEligibleListings =
                retainTargetEligibleListings(
                        priceEligibleListings,
                        configuration
                );


        if (targetEligibleListings.isEmpty()) {

            log.warn(
                    "[TARGET MATCHER] None of the {} price-eligible "
                            + "current-scan listings matches the configured "
                            + "target. No quota will be reserved and "
                            + "no negotiation will be started.",
                    priceEligibleListings.size()
            );

            return;
        }


        Long botId =
                context.getBot()
                        .getId();


        /*
         * Backend wylicza, ile nowych negocjacji
         * możemy rozpocząć z uwzględnieniem budżetu
         * i dziennego quota.
         */
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


        /*
         * DRY RUN nowych negocjacji.
         *
         * Nie rezerwujemy quota
         * i nie wysyłamy żadnej oferty.
         */
        if (!realOffersEnabled) {

            log.warn(
                    "[REAL OFFER DRY RUN] Real offers are disabled. "
                            + "{} current-scan listings passed all guards. "
                            + "Backend allows {} new negotiations. "
                            + "No quota will be reserved and "
                            + "no offer will be sent.",
                    targetEligibleListings.size(),
                    allowedNewNegotiations
            );

            return;
        }


        /*
         * Dodatkowy limit bezpieczeństwa
         * na jeden cykl workera.
         */
        int maximumOffersThisRun =
                Math.min(
                        allowedNewNegotiations,
                        maxRealOffersPerRun
                );


        log.warn(
                "[REAL OFFER] Real offers are enabled. "
                        + "Bot {} has {} current-scan price-eligible listings "
                        + "and may start {} new negotiations. "
                        + "This run is limited to {} real offer.",
                botId,
                targetEligibleListings.size(),
                allowedNewNegotiations,
                maximumOffersThisRun
        );


        int checkedListings =
                0;


        int startedNegotiations =
                0;


        for (
                ListingResponseDto listing
                : targetEligibleListings
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
                            + "Backend listing {}, marketplace listing {}, "
                            + "original price {}.",
                    checkedListings,
                    listing.id(),
                    listing.listingId(),
                    listing.originalPrice()
            );


            /*
             * CURRENT SCAN, PRICE GUARD oraz TARGET MATCHER
             * zostały wykonane przed rezerwacją quota.
             *
             * Dopiero tutaj rezerwujemy quota.
             */
            OfferQuotaReservationResponseDto quotaReservation =
                    offerQuotaClient.reserveSlot(
                            botId
                    );


            if (
                    !quotaReservation.reserved()
            ) {

                log.warn(
                        "[REAL OFFER] Daily offer quota exhausted for bot {}. "
                                + "Used: {}/{}, remaining: {}. "
                                + "No offer will be sent.",
                        botId,
                        quotaReservation.used(),
                        quotaReservation.limit(),
                        quotaReservation.remaining()
                );

                return;
            }


            NegotiationStartResult result;


            try {

                result =
                        negotiationExecutor
                                .startFirstNegotiation(
                                        listing
                                );

            } catch (Exception exception) {

                /*
                 * Nie zwalniamy quota.
                 *
                 * Playwright mógł zdążyć kliknąć submit,
                 * więc stan wysłania jest niejednoznaczny.
                 */
                log.error(
                        "[REAL OFFER] Failed after reserving quota for "
                                + "marketplace listing {}: {}. "
                                + "The quota slot will NOT be released because "
                                + "the delivery state is unknown.",
                        listing.listingId(),
                        getFriendlyErrorMessage(
                                exception
                        )
                );

                log.trace(
                        "[REAL OFFER] Full exception for marketplace listing {}.",
                        listing.listingId(),
                        exception
                );


                throw exception;
            }


            /*
             * Listing przestał być dostępny.
             * Oferta nie została wysłana.
             */
            if (
                    result
                            == NegotiationStartResult
                            .LISTING_UNAVAILABLE
            ) {

                releaseQuotaSlot(
                        botId,
                        listing,
                        "listing unavailable"
                );


                listingStatusUpdater.markUnavailable(
                        botId,
                        listing
                );


                continue;
            }


            /*
             * Vinted odrzuciło skonfigurowaną
             * pierwszą ofertę jako zbyt niską.
             */
            if (
                    result
                            == NegotiationStartResult
                            .OFFER_TOO_LOW
            ) {

                releaseQuotaSlot(
                        botId,
                        listing,
                        "offer too low"
                );


                listingStatusUpdater.markOfferTooLow(
                        botId,
                        listing
                );


                continue;
            }


            /*
             * Pierwsza oferta została faktycznie wysłana.
             * Quota pozostaje zużyte.
             */
            if (
                    result
                            == NegotiationStartResult
                            .STARTED
            ) {

                startedNegotiations++;


                log.warn(
                        "[REAL OFFER] A real negotiation was started "
                                + "for marketplace listing {}. "
                                + "Started negotiations during this run: {}. "
                                + "Daily quota used: {}/{}, remaining: {}.",
                        listing.listingId(),
                        startedNegotiations,
                        quotaReservation.used(),
                        quotaReservation.limit(),
                        quotaReservation.remaining()
                );


                /*
                 * Obecna logika celowo kończy cykl
                 * po rozpoczęciu jednej prawdziwej negocjacji.
                 */
                return;
            }


            /*
             * Nieznany wynik = bezpiecznie przerywamy.
             * Quota nie zwalniamy, bo stan wysłania
             * może być niejednoznaczny.
             */
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


    private List<ListingResponseDto>
    retainTargetEligibleListings(
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

            /*
             * KROK 1:
             * tytuł widoczny na karcie katalogowej.
             */
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


            /*
             * KROK 2:
             * slug URL.
             *
             * To jest analiza lokalna i nie powoduje żadnego
             * dodatkowego requestu do Vinted.
             */
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


            /*
             * KROK 3:
             * jeżeli pełny tytuł był już wcześniej pobrany,
             * wykorzystujemy cache.
             *
             * Cache hit NIE zużywa limitu detail requests.
             */
            boolean cached =
                    listingDetailTargetInspector
                            .hasCachedFullTitle(
                                    listing.listingId()
                            );


            if (cached) {

                boolean cachedMatches =
                        listingDetailTargetInspector
                                .matchesConfiguredTarget(
                                        listing,
                                        configuration
                                );


                if (cachedMatches) {

                    eligibleListings.add(
                            listing
                    );

                    matchedFromDetailCache++;

                } else {

                    rejectedFromDetailCache++;
                }


                continue;
            }


            /*
             * KROK 4:
             * dopiero teraz potrzebny byłby realny request
             * do strony konkretnego ogłoszenia.
             *
             * Nie wykonujemy więcej niż kilka takich wejść
             * w jednym cyklu.
             */
            if (
                    detailRequestsThisCycle
                            >= MAX_DETAIL_INSPECTIONS_PER_CYCLE
            ) {

                deferredByDetailLimit++;


                log.info(
                        "[TARGET DETAIL] Marketplace listing {} is still "
                                + "ambiguous, but the per-cycle detail limit "
                                + "({}) has already been reached. "
                                + "The listing is deferred safely to a later "
                                + "cycle before quota reservation.",
                        listing.listingId(),
                        MAX_DETAIL_INSPECTIONS_PER_CYCLE
                );


                continue;
            }


            /*
             * Pacing tylko pomiędzy realnymi wejściami na item page.
             */
            if (detailRequestsThisCycle > 0) {

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


                if (detailMatches) {

                    eligibleListings.add(
                            listing
                    );

                    matchedAfterDetailRequest++;

                } else {

                    rejectedAfterDetailRequest++;
                }

            } catch (VintedRateLimitException exception) {

                /*
                 * Jawnego rate limitu NIE ignorujemy i nie przechodzimy
                 * do następnego listing'u.
                 *
                 * Wyjątek idzie do BotWorker, który robi długi cooldown.
                 */
                log.warn(
                        "[RATE LIMIT] Vinted rate limit detected while "
                                + "inspecting marketplace listing {}. "
                                + "Stopping this work cycle immediately.",
                        listing.listingId()
                );


                throw exception;

            } catch (Exception exception) {

                /*
                 * Zwykły timeout / chwilowy błąd strony:
                 * fail-closed tylko dla tego listing'u.
                 *
                 * Nie rezerwujemy quota i nie wysyłamy oferty.
                 */
                detailInspectionFailures++;


                log.error(
                        "[TARGET DETAIL] Failed to inspect marketplace "
                                + "listing {}. It will be skipped for this "
                                + "cycle before quota reservation.",
                        listing.listingId(),
                        exception
                );
            }
        }


        log.info(
                "[TARGET MATCHER] Checked {} current-scan price-eligible "
                        + "listings. "
                        + "Catalog matches: {}, URL matches: {}, "
                        + "detail-cache matches: {}, detail-request matches: {}, "
                        + "catalog mismatches: {}, URL mismatches: {}, "
                        + "detail-cache mismatches: {}, "
                        + "detail-request mismatches: {}, "
                        + "detail requests this cycle: {}/{}, "
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

            /*
             * Nie zatrzymujemy całego workera.
             *
             * Jeżeli release się nie uda, quota pozostaje
             * zużyte, czyli zachowanie jest bezpieczne
             * względem limitu dziennego.
             */
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