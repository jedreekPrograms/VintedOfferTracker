package pl.flipbot.playwright.worker;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.browser.BrowserManager;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.login.LoginService;
import pl.flipbot.playwright.model.BotDetailsDto;
import pl.flipbot.playwright.negotiation.NegotiationExecutor;
import pl.flipbot.playwright.scanner.ListingScanner;

@Slf4j
@RequiredArgsConstructor
public class BotWorker implements Runnable {

    private final BotContext context;

    private final LoginService loginService;

    private final ListingScanner listingScanner;

    private final NegotiationExecutor negotiationExecutor;

    public BotWorker(BotDetailsDto bot,
                     BrowserManager browserManager) {
        this.context = new BotContext(
                bot,
                browserManager
        );

        this.loginService =
                new LoginService(context);

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

//            loginService.login();
//
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

        listingScanner.scan();

//        negotiationExecutor.processNegotiations();
    }
}
