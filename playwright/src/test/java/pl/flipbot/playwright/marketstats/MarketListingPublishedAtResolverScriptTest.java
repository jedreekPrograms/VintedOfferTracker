package pl.flipbot.playwright.marketstats;

import org.junit.After;
import org.junit.Test;
import pl.flipbot.playwright.scanner.model.Listing;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MarketListingPublishedAtResolverScriptTest {

    @After
    public void clearObservationContext() {
        MarketStatsObservationContext.clear(null);
    }

    @Test
    public void detailPageExtractionPrioritizesConfirmedUploadDateFieldBeforeFallbacks() {
        String script = MarketListingPublishedAtResolver.extractPublishedAtScript();

        String exactField = "item-attributes-upload_date";
        String exactValue = "[itemprop=\"upload_date\"]";
        String genericAdded = "item.text.toLowerCase() !== \"dodane\"";

        assertTrue(script.contains(exactField));
        assertTrue(script.contains(exactValue));
        assertTrue(script.contains(genericAdded));
        assertTrue(script.indexOf(exactField) < script.indexOf(genericAdded));
        assertTrue(script.contains("created_at_ts"));
        assertTrue(script.contains("application/ld+json"));
        assertFalse(script.contains("fetch("));
        assertFalse(script.contains("Promise.all"));
    }

    @Test
    public void everyUnresolvedListingIsSelectedWithoutThreeItemCap() {
        MarketStatsObservationContext.begin(
                25L,
                List.of(),
                List.of(),
                false
        );

        List<Listing> listings = new ArrayList<>();

        for (int index = 1; index <= 8; index++) {
            listings.add(listing(
                    String.valueOf(9000 + index),
                    "/items/" + (9000 + index) + "-test"
            ));
        }

        List<Listing> selected =
                MarketListingPublishedAtResolver.detailCandidates(listings);

        assertEquals(8, selected.size());
    }

    @Test
    public void storedPublicationTimeIsReusedButMissingAndNewListingsAreOpened() {
        MarketStatsObservationContext.begin(
                25L,
                List.of("known-complete", "known-missing"),
                List.of("known-missing"),
                false
        );

        List<Listing> selected =
                MarketListingPublishedAtResolver.detailCandidates(
                        List.of(
                                listing(
                                        "known-complete",
                                        "/items/100-known-complete"
                                ),
                                listing(
                                        "known-missing",
                                        "/items/101-known-missing"
                                ),
                                listing(
                                        "brand-new",
                                        "/items/102-brand-new"
                                )
                        )
                );

        assertEquals(2, selected.size());
        assertEquals("known-missing", selected.get(0).getId());
        assertEquals("brand-new", selected.get(1).getId());
    }

    private Listing listing(String id, String url) {
        Listing listing = new Listing();
        listing.setId(id);
        listing.setUrl(url);
        return listing;
    }
}
