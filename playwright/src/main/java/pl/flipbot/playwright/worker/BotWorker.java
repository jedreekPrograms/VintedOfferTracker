package pl.flipbot.playwright.worker;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.browser.BrowserManager;
import pl.flipbot.playwright.login.LoginService;
import pl.flipbot.playwright.model.BotDetailsDto;
import pl.flipbot.playwright.negotiation.NegotiationExecutor;
import pl.flipbot.playwright.scanner.ListingScanner;

@Slf4j
@RequiredArgsConstructor
public class BotWorker implements Runnable {

    private final BotDetailsDto bot;

    private final BrowserContext context;

    private final Page page;

    private final LoginService loginService;

    private final ListingScanner listingScanner;

    private final NegotiationExecutor negotiationExecutor;

    public BotWorker(BotDetailsDto bot,
                     BrowserManager browserManager) {
        this.bot = bot;

        this.context = browserManager.createContext();

        this.page = context.newPage();

        this.loginService = new LoginService(page, bot);

        this.listingScanner = new ListingScanner(page, bot);

        this.negotiationExecutor = new NegotiationExecutor(page, bot);


    }

    @Override
    public void run() {

        log.info(
                "Worker started for bot {}",
                bot.getId()
        );

        try {

            loginService.login();

            while (!Thread.currentThread().isInterrupted()) {

                doWork();

                Thread.sleep(3000);

            }

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

        } catch (Exception e) {

            log.error(
                    "Worker {} failed",
                    bot.getId(),
                    e
            );

        } finally {

            context.close();

            log.info(
                    "Worker stopped {}",
                    bot.getId()
            );

        }

    }

    private void doWork() {

        listingScanner.scan();

        negotiationExecutor.processNegotiations();
    }
}
