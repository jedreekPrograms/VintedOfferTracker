package pl.flipbot.playwright.marketplace;

import com.microsoft.playwright.Page;
import lombok.RequiredArgsConstructor;
import pl.flipbot.playwright.context.BotContext;

@RequiredArgsConstructor
public class MarketplaceNavigator {

    private final BotContext context;

    public void goToHome() {

        context.getPage().navigate(
                MarketplaceUrls.HOME
        );

    }

    public void goToCatalog() {

        context.getPage().navigate(
                MarketplaceUrls.CATALOG
        );

    }

    public void goToInbox() {

        context.getPage().navigate(
                MarketplaceUrls.INBOX
        );

    }

    public Page page() {
        return context.getPage();
    }
}
