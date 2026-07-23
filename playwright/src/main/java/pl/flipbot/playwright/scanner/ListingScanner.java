package pl.flipbot.playwright.scanner;

import com.microsoft.playwright.Locator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.scanner.model.Listing;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class ListingScanner {

    private final BotContext context;

    private final ListingParser parser = new ListingParser();

    public List<Listing> scan() {

        Locator items = context.getPage()
                        .locator(ListingSelectors.ITEM);

        log.info(
                "Scanning listings for bot {}",
                context.getBot().getId()
        );

        List<Listing> listings = new ArrayList<>();

        for(int i = 0; i < items.count(); i++) {

            Listing listing = parser.parse(
                    items.nth(i)
            );

            listings.add(listing);

        }

        return listings;

    }

}