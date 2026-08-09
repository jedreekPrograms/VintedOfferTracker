package pl.flipbot.playwright.marketplace;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitUntilState;
import lombok.RequiredArgsConstructor;
import pl.flipbot.playwright.context.BotContext;

@RequiredArgsConstructor
public class MarketplaceNavigator {

    private final BotContext context;

    public void goToHome() {
        navigate(MarketplaceUrls.HOME);
    }

    public void goToCatalog() {
        navigate(MarketplaceUrls.CATALOG);
    }

    public void goToInbox() {
        navigate(MarketplaceUrls.INBOX);
    }

    public Page page() {
        return context.getPage();
    }

    private void navigate(String url) {

        Page page =
                context.getPage();

        page.navigate(
                url,
                new Page.NavigateOptions()
                        .setWaitUntil(
                                WaitUntilState.DOMCONTENTLOADED
                        )
                        .setTimeout(
                                30_000
                        )
        );
    }
}