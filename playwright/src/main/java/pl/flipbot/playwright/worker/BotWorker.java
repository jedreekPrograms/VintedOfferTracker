package pl.flipbot.playwright.worker;

import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.listing.ListingClient;
import pl.flipbot.playwright.browser.BrowserManager;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.filters.FilterService;
import pl.flipbot.playwright.login.LoginService;
import pl.flipbot.playwright.marketplace.MarketplaceNavigator;
import pl.flipbot.playwright.model.BotDetailsDto;
import pl.flipbot.playwright.negotiation.NegotiationExecutor;
import pl.flipbot.playwright.processing.ListingProcessingService;
import pl.flipbot.playwright.scanner.ListingScanner;

@Slf4j
public class BotWorker implements Runnable {

    private final BotContext context;

    private final LoginService loginService;

    private final MarketplaceNavigator marketplaceNavigator;

    private final FilterService filterService;

    private final ListingScanner listingScanner;

    private final ListingProcessingService listingProcessingService;

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
                new LoginService(context);

        this.marketplaceNavigator =
                new MarketplaceNavigator(context);

        this.filterService =
                new FilterService(context);

        this.listingScanner =
                new ListingScanner(context);

        ListingClient listingClient =
                new ListingClient();

        this.listingProcessingService =
                new ListingProcessingService(
                        context,
                        listingClient
                );

        this.negotiationExecutor =
                new NegotiationExecutor(context);

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

            Thread.sleep(10_000);

        } catch (InterruptedException exception) {

            Thread.currentThread().interrupt();

        } catch (Exception exception) {

            log.error(
                    "Worker {} failed",
                    context.getBot().getId(),
                    exception
            );

        } finally {

            context.close();

            log.info(
                    "Worker stopped {}",
                    context.getBot().getId()
            );

        }

    }

    private void doWork() {

        marketplaceNavigator.goToCatalog();

        filterService.applyFilters(
                context.getBot()
        );

        var scannedListings =
                listingScanner.scan();

        var claimedListings =
                listingProcessingService.process(
                        scannedListings
                );

        log.info(
                "Bot {} has {} new listings ready for further processing",
                context.getBot().getId(),
                claimedListings.size()
        );

    }

}