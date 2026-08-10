package pl.flipbot.playwright.negotiation;

import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.listing.ListingClient;
import pl.flipbot.playwright.api.listing.ListingStatusUpdater;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.api.quota.OfferQuotaClient;
import pl.flipbot.playwright.api.quota.dto.OfferQuotaReservationResponseDto;
import pl.flipbot.playwright.context.BotContext;

import java.util.List;

@Slf4j
public class NewNegotiationProcessor {

    private final BotContext context;

    private final ListingClient listingClient;

    private final OfferQuotaClient offerQuotaClient;

    private final ListingStatusUpdater
            listingStatusUpdater;

    private final NegotiationExecutor
            negotiationExecutor;

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
                    priceEligibleListings.size(),
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
                priceEligibleListings.size(),
                allowedNewNegotiations,
                maximumOffersThisRun
        );


        int checkedListings =
                0;


        int startedNegotiations =
                0;


        for (
                ListingResponseDto listing
                : priceEligibleListings
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
             * CURRENT SCAN oraz PRICE GUARD
             * zostały wykonane wcześniej w BotWorker.
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