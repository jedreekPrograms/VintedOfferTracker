package pl.flipbot.playwright.marketplace;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.WaitUntilState;
import lombok.RequiredArgsConstructor;
import pl.flipbot.playwright.context.BotContext;

@RequiredArgsConstructor
public class MarketplaceNavigator {

    private static final String CATALOG_FILTER_TRIGGER_TEST_ID =
            "catalog--catalog-filter--trigger";

    private static final double NAVIGATION_TIMEOUT_MS =
            30_000;

    private static final double CATALOG_READY_TIMEOUT_MS =
            10_000;

    private static final double CATALOG_SETTLE_DELAY_MS =
            500;


    private final BotContext context;


    public void goToHome() {

        navigate(
                MarketplaceUrls.HOME
        );
    }


    public void goToCatalog() {

        navigate(
                MarketplaceUrls.CATALOG
        );


        /*
         * DOMContentLoaded nie oznacza jeszcze, że React Vinted
         * wyrenderował interaktywne filtry katalogu.
         *
         * Wcześniej BotWorker natychmiast wywoływał CategoryNavigator
         * i pierwsza próba często kończyła się timeoutem na "Elektronika".
         */
        Page page =
                context.getPage();


        Locator categoryFilterTrigger =
                page.getByTestId(
                        CATALOG_FILTER_TRIGGER_TEST_ID
                );


        categoryFilterTrigger.waitFor(
                new Locator.WaitForOptions()
                        .setState(
                                WaitForSelectorState.VISIBLE
                        )
                        .setTimeout(
                                CATALOG_READY_TIMEOUT_MS
                        )
        );


        /*
         * Mały settle po pojawieniu się triggera.
         * Nie czekamy na networkidle, bo Vinted utrzymuje
         * requesty w tle i byłoby to mniej stabilne.
         */
        page.waitForTimeout(
                CATALOG_SETTLE_DELAY_MS
        );
    }


    public void goToInbox() {

        navigate(
                MarketplaceUrls.INBOX
        );
    }


    public Page page() {

        return context.getPage();
    }


    private void navigate(
            String url
    ) {

        Page page =
                context.getPage();


        page.navigate(
                url,
                new Page.NavigateOptions()
                        .setWaitUntil(
                                WaitUntilState.DOMCONTENTLOADED
                        )
                        .setTimeout(
                                NAVIGATION_TIMEOUT_MS
                        )
        );
    }
}