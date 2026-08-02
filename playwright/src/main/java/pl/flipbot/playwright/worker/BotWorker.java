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
import pl.flipbot.playwright.model.BotDetailsDto;
import pl.flipbot.playwright.negotiation.NegotiationExecutor;
import pl.flipbot.playwright.negotiation.NegotiationStartResult;
import pl.flipbot.playwright.processing.ListingProcessingService;
import pl.flipbot.playwright.scanner.ListingScanner;

import java.math.BigDecimal;

@Slf4j
public class BotWorker implements Runnable {

    /*
     * Zabezpieczenie przed przypadkowym wysłaniem prawdziwej oferty.
     *
     * false:
     * bot skanuje i przygotowuje dane, ale nie wysyła oferty.
     *
     * true:
     * bot może wysłać prawdziwą ofertę.
     */
    private static final boolean REAL_OFFERS_ENABLED =
            false;

    /*
     * Nawet po włączeniu prawdziwych ofert bot wyśle maksymalnie
     * jedną ofertę podczas pojedynczego uruchomienia.
     */
    private static final int MAX_REAL_OFFERS_PER_RUN =
            1;

    private final BotContext context;

    private final LoginService loginService;

    private final MarketplaceNavigator marketplaceNavigator;

    private final FilterService filterService;

    private final ListingScanner listingScanner;

    private final ListingProcessingService listingProcessingService;

    private final ListingClient listingClient;

    private final NegotiationExecutor negotiationExecutor;

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

    }

    @Override
    public void run() {

        log.info(
                "Worker started for bot {}",
                context.getBot().getId()
        );

        try {

            loginService.login();

            doWork();

            Thread.sleep(
                    10_000
            );

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

        var negotiatingListings =
                listingClient.getNegotiatingListings(
                        botId
                );

        log.info(
                "Bot {} currently has {} active negotiations",
                botId,
                negotiatingListings.size()
        );

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

        var discoveredListings =
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
                            + "Set REAL_OFFERS_ENABLED to true "
                            + "to send one controlled test offer."
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

        for (ListingResponseDto listing
                : discoveredListings) {

            if (startedNegotiations
                    >= maximumOffersThisRun) {

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

            if (result
                    == NegotiationStartResult
                    .LISTING_UNAVAILABLE) {

                markUnavailable(
                        botId,
                        listing
                );

                continue;

            }

            if (result
                    == NegotiationStartResult
                    .OFFER_TOO_LOW) {

                markOfferTooLow(
                        botId,
                        listing
                );

                continue;

            }

            if (result
                    == NegotiationStartResult.STARTED) {

                startedNegotiations++;

                log.warn(
                        "[REAL OFFER] A real negotiation was started "
                                + "for marketplace listing {}. "
                                + "Started negotiations during this run: {}.",
                        listing.listingId(),
                        startedNegotiations
                );

                /*
                 * Twarde zatrzymanie po pierwszej prawdziwej ofercie.
                 */
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

    private void markUnavailable(
            Long botId,
            ListingResponseDto listing
    ) {

        UpdateListingRequestDto request =
                createStatusUpdateRequest(
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
                createStatusUpdateRequest(
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

    private UpdateListingRequestDto createStatusUpdateRequest(
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