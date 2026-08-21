package pl.flipbot.playwright.target;

import org.junit.Test;
import pl.flipbot.playwright.model.BotConfigurationDto;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ListingTargetMatcherTest {

    private final ListingTargetMatcher matcher =
            new ListingTargetMatcher();

    @Test
    public void searchQueryAllowsSamsungTechnicalProductCodeForGalaxyS25() {
        assertTrue(
                matcher.matchesFullTitle(
                        "Samsung Galaxy S25 SM-S931 12/128GB Enterprise Edition Srebrny",
                        samsungSearchQuery("Galaxy S25")
                )
        );
    }

    @Test
    public void searchQueryGalaxyS25RejectsUltraVariant() {
        assertFalse(
                matcher.matchesFullTitle(
                        "Samsung Galaxy S25 Ultra SM-S938 12/256GB",
                        samsungSearchQuery("Galaxy S25")
                )
        );
    }

    @Test
    public void searchQueryGalaxyS25RejectsEdgeVariant() {
        assertFalse(
                matcher.matchesFullTitle(
                        "Samsung Galaxy S25 Edge 12/256GB",
                        samsungSearchQuery("Galaxy S25")
                )
        );
    }

    @Test
    public void searchQueryGalaxyS25RejectsDifferentGeneration() {
        assertFalse(
                matcher.matchesFullTitle(
                        "Samsung Galaxy S24 SM-S921 8/256GB",
                        samsungSearchQuery("Galaxy S25")
                )
        );
    }

    @Test
    public void searchQueryGalaxyS25PlusAcceptsItsTechnicalProductCode() {
        assertTrue(
                matcher.matchesFullTitle(
                        "Samsung Galaxy S25+ SM-S936 12/256GB",
                        samsungSearchQuery("Galaxy S25+")
                )
        );
    }

    @Test
    public void searchQueryGalaxyTabS11UltraAcceptsExactVariant() {
        assertTrue(
                matcher.matchesFullTitle(
                        "Samsung Galaxy Tab S11 Ultra 5G 256GB",
                        samsungSearchQuery("Galaxy Tab S11 Ultra")
                )
        );
    }

    @Test
    public void searchQueryGalaxyTabS11UltraAcceptsCompactUltraSpelling() {
        assertTrue(
                matcher.matchesFullTitle(
                        "Samsung Tab S11Ultra 256GB",
                        samsungSearchQuery("Galaxy Tab S11 Ultra")
                )
        );
    }

    @Test
    public void searchQueryGalaxyTabS11UltraRejectsPreviousGeneration() {
        assertFalse(
                matcher.matchesFullTitle(
                        "Samsung Galaxy Tab S10 Ultra 5G 256GB",
                        samsungSearchQuery("Galaxy Tab S11 Ultra")
                )
        );
    }

    @Test
    public void searchQueryGalaxyTabS11UltraRejectsFeVariant() {
        assertFalse(
                matcher.matchesFullTitle(
                        "Samsung Galaxy Tab S11 FE 5G",
                        samsungSearchQuery("Galaxy Tab S11 Ultra")
                )
        );
    }

    @Test
    public void searchQueryGalaxyTabS11UltraRejectsPlainS11() {
        assertFalse(
                matcher.matchesFullTitle(
                        "Samsung Galaxy Tab S11 5G 256GB",
                        samsungSearchQuery("Galaxy Tab S11 Ultra")
                )
        );
    }

    @Test
    public void vintedModelDoesNotReinterpretListingsAfterExactFilterSelection() {
        BotConfigurationDto configuration = samsungVintedModel("Galaxy S25");

        assertTrue(
                matcher.matchesFullTitle(
                        "Seller wrote a completely custom title",
                        configuration
                )
        );
        assertTrue(
                matcher.matchesFullTitle(
                        "Samsung Galaxy S25 Edge text would be irrelevant here because the exact Vinted filter already defines the result set",
                        configuration
                )
        );
    }

    private BotConfigurationDto samsungSearchQuery(String query) {
        BotConfigurationDto configuration = new BotConfigurationDto();
        configuration.setTargetMode("SEARCH_QUERY");
        configuration.setBrand("Samsung");
        configuration.setSearchQuery(query);
        return configuration;
    }

    private BotConfigurationDto samsungVintedModel(String model) {
        BotConfigurationDto configuration = new BotConfigurationDto();
        configuration.setTargetMode("VINTED_MODEL");
        configuration.setBrand("Samsung");
        configuration.setModel(model);
        return configuration;
    }
}
