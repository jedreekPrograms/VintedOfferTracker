package pl.flipbot.playwright.marketstats;

import org.junit.Test;
import pl.flipbot.playwright.model.BotConfigurationDto;
import pl.flipbot.playwright.scanner.model.Listing;
import pl.flipbot.playwright.target.ListingTargetMatcher;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MarketStatsCollectorTargetSelectionTest {

    @Test
    public void vintedModelTrustsTheAlreadyVerifiedCatalogPopulation() {
        BotConfigurationDto configuration = new BotConfigurationDto();
        configuration.setTargetMode("VINTED_MODEL");
        configuration.setBrand("Apple");
        configuration.setModel("iPad 10.9 (2022)");

        Listing listing = new Listing();
        listing.setId("1234567890");
        listing.setTitle(null);
        listing.setUrl(null);

        MarketStatsCollector collector = new MarketStatsCollector(null, null);

        assertTrue(
                MarketStatsCollector.trustsVerifiedVintedCatalogPopulation(
                        configuration
                )
        );
        assertTrue(
                collector.matchesTarget(
                        listing,
                        configuration,
                        false,
                        new ListingTargetMatcher()
                )
        );
    }

    @Test
    public void searchQueryKeepsPerListingFilteringAndAccessoryRejection() {
        BotConfigurationDto configuration = new BotConfigurationDto();
        configuration.setTargetMode("SEARCH_QUERY");
        configuration.setBrand("Samsung");
        configuration.setSearchQuery("Galaxy S25");

        Listing listing = new Listing();
        listing.setId("1234567890");
        listing.setTitle("Galaxy S25 case");
        listing.setUrl("https://www.vinted.pl/items/1234567890-galaxy-s25-case");

        MarketStatsCollector collector = new MarketStatsCollector(null, null);

        assertFalse(
                MarketStatsCollector.trustsVerifiedVintedCatalogPopulation(
                        configuration
                )
        );
        assertFalse(
                collector.matchesTarget(
                        listing,
                        configuration,
                        true,
                        new ListingTargetMatcher()
                )
        );
    }
}
