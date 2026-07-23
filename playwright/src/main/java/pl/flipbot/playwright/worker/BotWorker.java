package pl.flipbot.playwright.worker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.browser.BrowserManager;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.filters.FilterService;
import pl.flipbot.playwright.login.LoginService;
import pl.flipbot.playwright.marketplace.MarketplaceNavigator;
import pl.flipbot.playwright.model.BotDetailsDto;
import pl.flipbot.playwright.negotiation.NegotiationExecutor;
import pl.flipbot.playwright.scanner.ListingScanner;
import pl.flipbot.playwright.testdata.TestBotFactory;

@Slf4j
@RequiredArgsConstructor
public class BotWorker implements Runnable {

    private final BotContext context;

    private final LoginService loginService;

    private final MarketplaceNavigator marketplaceNavigator;

    private final FilterService filterService;

    private final ListingScanner listingScanner;

    private final NegotiationExecutor negotiationExecutor;

    public BotWorker(BotDetailsDto bot,
                     BrowserManager browserManager) {

        bot = TestBotFactory.configure(bot);

        this.context = new BotContext(
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

//            while (!Thread.currentThread().isInterrupted()) {
//
//                doWork();
//
//                Thread.sleep(3000);
//
//            }

            loginService.login();

            doWork();

            Thread.sleep(10000);

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

        } catch (Exception e) {

            log.error(
                    "Worker {} failed",
                    context.getBot().getId(),
                    e
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

        listingScanner.scan();

        var listings = listingScanner.scan();

        for (var listing : listings) {

            log.info(
                    "{} | {} | {} zł",
                    listing.getId(),
                    listing.getCondition(),
                    listing.getPrice()
            );
        }

    }

}