package pl.flipbot.playwright.scanner;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.scanner.model.Listing;
import pl.flipbot.playwright.scanner.model.ListingSnapshot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class ListingScanner {

    private static final double LISTINGS_TIMEOUT =
            15_000;

    private final BotContext context;

    private final ListingParser parser =
            new ListingParser();

    private final ObjectMapper objectMapper =
            new ObjectMapper();

    public List<Listing> scan() {

        log.info(
                "Scanning listings for bot {}",
                context.getBot().getId()
        );

        Locator items = context.getPage()
                .locator(ListingSelectors.ITEM);

        if (!waitForListings(items)) {

            log.warn(
                    "No listings found for bot {} using selector {}",
                    context.getBot().getId(),
                    ListingSelectors.ITEM
            );

            return List.of();

        }

        List<ListingSnapshot> snapshots =
                readSnapshots(items);

        log.info(
                "Found {} listing snapshots",
                snapshots.size()
        );

        Map<String, Listing> uniqueListings =
                new LinkedHashMap<>();

        int skippedListings = 0;

        for (ListingSnapshot snapshot : snapshots) {

            try {

                Listing listing =
                        parser.parse(snapshot);

                if (listing.getId() == null) {

                    skippedListings++;

                    log.warn(
                            "Skipping listing without id: {}",
                            snapshot.testId()
                    );

                    continue;

                }

                if (listing.getTitle() == null) {

                    skippedListings++;

                    log.warn(
                            "Skipping listing {} without title",
                            listing.getId()
                    );

                    continue;

                }

                if (listing.getPrice() == null) {

                    skippedListings++;

                    log.warn(
                            "Skipping listing {} without price",
                            listing.getId()
                    );

                    continue;

                }

                Listing previousListing =
                        uniqueListings.putIfAbsent(
                                listing.getId(),
                                listing
                        );

                if (previousListing != null) {

                    log.debug(
                            "Skipping duplicated listing {}",
                            listing.getId()
                    );

                }

            } catch (RuntimeException exception) {

                skippedListings++;

                log.warn(
                        "Could not parse listing snapshot {}: {}",
                        snapshot.testId(),
                        exception.getMessage()
                );

                log.debug(
                        "Full listing parsing error",
                        exception
                );

            }

        }

        List<Listing> listings =
                new ArrayList<>(
                        uniqueListings.values()
                );

        log.info(
                "Listing scan completed. Parsed: {}, skipped: {}",
                listings.size(),
                skippedListings
        );

        return listings;

    }

    private boolean waitForListings(
            Locator items
    ) {

        try {

            items.first().waitFor(
                    new Locator.WaitForOptions()
                            .setState(
                                    WaitForSelectorState.ATTACHED
                            )
                            .setTimeout(
                                    LISTINGS_TIMEOUT
                            )
            );

            return true;

        } catch (TimeoutError exception) {

            return false;

        }

    }

    private List<ListingSnapshot> readSnapshots(
            Locator items
    ) {

        Map<String, String> selectors = Map.of(
                "title", ListingSelectors.TITLE,
                "condition", ListingSelectors.CONDITION,
                "price", ListingSelectors.PRICE,
                "link", ListingSelectors.LINK,
                "image", ListingSelectors.IMAGE,
                "favorites", ListingSelectors.FAVORITES
        );

        Object result = items.evaluateAll(
                """
                (elements, selectors) => {

                    const readText = (item, selector) => {

                        const element =
                            item.querySelector(selector);

                        if (!element) {
                            return null;
                        }

                        const value =
                            element.textContent;

                        return value
                            ? value.trim()
                            : null;
                    };

                    const readAttribute =
                        (item, selector, attribute) => {

                            const element =
                                item.querySelector(selector);

                            if (!element) {
                                return null;
                            }

                            return element.getAttribute(
                                attribute
                            );
                        };

                    return elements.map(item => ({

                        testId:
                            item.getAttribute(
                                'data-testid'
                            ),

                        title:
                            readText(
                                item,
                                selectors.title
                            ),

                        condition:
                            readText(
                                item,
                                selectors.condition
                            ),

                        price:
                            readText(
                                item,
                                selectors.price
                            ),

                        url:
                            readAttribute(
                                item,
                                selectors.link,
                                'href'
                            ),

                        imageUrl:
                            readAttribute(
                                item,
                                selectors.image,
                                'src'
                            ),

                        favoriteCount:
                            readText(
                                item,
                                selectors.favorites
                            )

                    }));

                }
                """,
                selectors
        );

        return objectMapper.convertValue(
                result,
                new TypeReference<
                        List<ListingSnapshot>
                        >() {
                }
        );

    }

}