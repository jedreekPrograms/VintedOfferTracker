package pl.flipbot.playwright.target;

import org.junit.Test;
import pl.flipbot.playwright.model.BotConfigurationDto;

import static org.junit.Assert.assertEquals;
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
    public void vintedModelRejectsWrongPersistedBacklogModel() {
        assertEquals(
                ListingTargetAssessment.MISMATCH,
                matcher.assessVisibleText(
                        "Samsung Galaxy Tab S9 FE+",
                        samsungVintedModel("Galaxy S25")
                )
        );
    }

    @Test
    public void vintedModelRejectsGalaxyTabBacklogWithoutGenerationKey() {
        assertEquals(
                ListingTargetAssessment.MISMATCH,
                matcher.assessVisibleText(
                        "Samsung Galaxy Tab Active 3",
                        samsungVintedModel("Galaxy S25")
                )
        );
        assertEquals(
                ListingTargetAssessment.MISMATCH,
                matcher.assessVisibleText(
                        "Samsung Galaxy Tab S",
                        samsungVintedModel("Galaxy S25")
                )
        );
    }

    @Test
    public void vintedModelRejectsDifferentFamily() {
        assertEquals(
                ListingTargetAssessment.MISMATCH,
                matcher.assessVisibleText(
                        "Samsung Galaxy Z Fold5",
                        samsungVintedModel("Galaxy S25")
                )
        );
    }

    @Test
    public void vintedModelGenericSellerTitleRequiresLiveIdentity() {
        assertEquals(
                ListingTargetAssessment.NEEDS_DETAIL_INSPECTION,
                matcher.assessVisibleText(
                        "Tablet z wyświetlaczem do wymiany",
                        samsungVintedModel("Galaxy S25")
                )
        );
    }

    @Test
    public void vintedModelExactStoredTitleIsPositiveProof() {
        assertEquals(
                ListingTargetAssessment.MATCH,
                matcher.assessVisibleText(
                        "Samsung Galaxy S25 128 GB",
                        samsungVintedModel("Galaxy S25")
                )
        );
    }

    @Test
    public void vintedModelFullTitleNoLongerBlindlyTrustsBotBacklog() {
        BotConfigurationDto configuration = samsungVintedModel("Galaxy S25");

        assertFalse(
                matcher.matchesFullTitle(
                        "Seller wrote a completely custom title",
                        configuration
                )
        );
        assertFalse(
                matcher.matchesFullTitle(
                        "Samsung Galaxy Tab S9 FE+",
                        configuration
                )
        );
        assertTrue(
                matcher.matchesFullTitle(
                        "Samsung Galaxy S25",
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
