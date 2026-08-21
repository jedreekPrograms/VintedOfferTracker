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
        assertMatch(
                "Galaxy S25",
                "Samsung Galaxy S25 SM-S931 12/128GB Enterprise Edition Srebrny"
        );
    }

    @Test
    public void searchQueryGalaxyS25RejectsOtherS25VariantsAndGeneration() {
        assertMismatch("Galaxy S25", "Samsung Galaxy S25 Ultra SM-S938 12/256GB");
        assertMismatch("Galaxy S25", "Samsung Galaxy S25 Edge 12/256GB");
        assertMismatch("Galaxy S25", "Samsung Galaxy S25 FE 8/256GB");
        assertMismatch("Galaxy S25", "Samsung Galaxy S25+ 12/256GB");
        assertMismatch("Galaxy S25", "Samsung Galaxy S24 SM-S921 8/256GB");
    }

    @Test
    public void searchQueryGalaxyS25PlusAcceptsPlusAliasesButNotPlainOrUltra() {
        assertMatch("Galaxy S25+", "Samsung Galaxy S25+ SM-S936 12/256GB");
        assertMatch("Galaxy S25+", "Samsung S25 Plus 12/256GB");
        assertMatch("Galaxy S25 Plus", "Samsung GalaxyS25+ 256GB");
        assertMatch("Galaxy S25Plus", "Samsung S25+ 256GB");

        assertMismatch("Galaxy S25+", "Samsung Galaxy S25 12/256GB");
        assertMismatch("Galaxy S25+", "Samsung Galaxy S25 Ultra 12/256GB");
    }

    @Test
    public void searchQueryGalaxyTabS11UltraAcceptsSpacingAndCompactAliases() {
        assertMatch(
                "Galaxy Tab S11 Ultra",
                "Samsung Galaxy Tab S11 Ultra 5G 256GB"
        );
        assertMatch(
                "Galaxy Tab S11 Ultra",
                "Samsung Tab S11Ultra 256GB"
        );
        assertMatch(
                "Galaxy Tab S11 Ultra",
                "Samsung GalaxyTabS11Ultra 256GB"
        );
    }

    @Test
    public void searchQueryGalaxyTabS11UltraRejectsWrongGenerationOrVariant() {
        assertMismatch(
                "Galaxy Tab S11 Ultra",
                "Samsung Galaxy Tab S10 Ultra 5G 256GB"
        );
        assertMismatch(
                "Galaxy Tab S11 Ultra",
                "Samsung Galaxy Tab S11 FE 5G"
        );
        assertMismatch(
                "Galaxy Tab S11 Ultra",
                "Samsung Galaxy Tab S11 5G 256GB"
        );
        assertMismatch(
                "Galaxy Tab S11 Ultra",
                "Samsung Galaxy Tab S11+ 256GB"
        );
    }

    @Test
    public void searchQueryGalaxyTabS10PlusUnderstandsPlusAndCompactTabSpelling() {
        assertMatch("Galaxy Tab S10+", "Samsung Galaxy Tab S10+ 5G 256GB");
        assertMatch("Galaxy Tab S10+", "Samsung Tab S10 Plus 256GB");
        assertMatch("Galaxy Tab S10+", "Samsung TabS10Plus 256GB");
        assertMatch("Galaxy Tab S10 Plus", "Samsung GalaxyTabS10+ 256GB");

        assertMismatch("Galaxy Tab S10+", "Samsung Galaxy Tab S10 256GB");
        assertMismatch("Galaxy Tab S10+", "Samsung Galaxy Tab S10 Ultra 256GB");
        assertMismatch("Galaxy Tab S10+", "Samsung Galaxy Tab S9+ 256GB");
    }

    @Test
    public void phoneSSeriesDoesNotAccidentallyMatchTabWithSameSNumber() {
        assertMismatch("Galaxy S10+", "Samsung Galaxy Tab S10+ 256GB");
        assertMismatch("Galaxy S25", "Samsung Galaxy Tab S25 256GB");
    }

    @Test
    public void searchQueryGalaxyZFoldAcceptsCommonSellerSpellings() {
        assertMatch("Galaxy Z Fold 7", "Samsung Galaxy Z Fold 7 512GB");
        assertMatch("Galaxy Z Fold 7", "Samsung Galaxy ZFold7 512GB");
        assertMatch("Galaxy Z Fold 7", "Samsung Fold7 512GB");
        assertMatch("Galaxy Z Fold 7", "Samsung GalaxyZFold7 512GB");

        assertMismatch("Galaxy Z Fold 7", "Samsung Galaxy Z Fold 6 512GB");
        assertMismatch("Galaxy Z Fold 7", "Samsung Galaxy Z Flip 7 512GB");
    }

    @Test
    public void searchQueryGalaxyZFlipAcceptsCommonSellerSpellings() {
        assertMatch("Galaxy Z Flip 7", "Samsung Galaxy Z Flip 7 256GB");
        assertMatch("Galaxy Z Flip 7", "Samsung ZFlip7 256GB");
        assertMatch("Galaxy Z Flip 7", "Samsung Flip7 256GB");

        assertMismatch("Galaxy Z Flip 7", "Samsung Galaxy Z Flip 6 256GB");
        assertMismatch("Galaxy Z Flip 7", "Samsung Galaxy Z Fold 7 256GB");
    }

    @Test
    public void searchQueryGalaxyASeriesKeepsGenerationStrict() {
        assertMatch("Galaxy A56", "Samsung Galaxy A56 5G 8/256GB");
        assertMatch("Galaxy A56", "Samsung A56 5G 128GB");
        assertMismatch("Galaxy A56", "Samsung Galaxy A55 5G 256GB");
        assertMismatch("Galaxy A56", "Samsung Galaxy A56 Ultra 256GB");
    }

    @Test
    public void searchQueryJoinedVariantNamesStillRejectUnexpectedVariant() {
        assertMismatch("Galaxy S25", "Samsung GalaxyS25Ultra 256GB");
        assertMismatch("Galaxy S25", "Samsung S25Edge 256GB");
        assertMismatch("Galaxy Tab S10", "Samsung TabS10Plus 256GB");
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

    private void assertMatch(String query, String title) {
        assertTrue(
                "Expected query '" + query + "' to match title '" + title + "'",
                matcher.matchesFullTitle(title, samsungSearchQuery(query))
        );
    }

    private void assertMismatch(String query, String title) {
        assertFalse(
                "Expected query '" + query + "' NOT to match title '" + title + "'",
                matcher.matchesFullTitle(title, samsungSearchQuery(query))
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
