package pl.flipbot.playwright.scanner;

import com.microsoft.playwright.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.context.BotContext;

@Slf4j
@RequiredArgsConstructor
public class ListingScanner {

    private static final String CATALOG_URL =
            "https://www.vinted.pl/catalog";

    private final BotContext context;

    public void scan() {

        Page page = context.getPage();

        log.info(
                "Opening catalog for bot {}",
                context.getBot().getId()
        );

        page.navigate(CATALOG_URL);

        page.waitForLoadState();

        log.info(
                "Catalog opened successfully."
        );

    }

}