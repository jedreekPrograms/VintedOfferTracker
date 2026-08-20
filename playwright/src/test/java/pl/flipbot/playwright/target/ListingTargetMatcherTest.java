package pl.flipbot.playwright.target;

import org.junit.Test;
import pl.flipbot.playwright.model.BotConfigurationDto;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ListingTargetMatcherTest {

    private final ListingTargetMatcher matcher =
            new ListingTargetMatcher();

    @Test
    public void samsungTechnicalProductCodeDoesNotConflictWithGalaxyS25() {
        assertTrue(
                matcher.matchesFullTitle(
                        "Samsung Galaxy S25 SM-S931 12/128GB Enterprise Edition Srebrny",
                        samsungModel("Galaxy S25")
                )
        );
    }

    @Test
    public void galaxyS25StillRejectsUltraVariant() {
        assertFalse(
                matcher.matchesFullTitle(
                        "Samsung Galaxy S25 Ultra SM-S938 12/256GB",
                        samsungModel("Galaxy S25")
                )
        );
    }

    @Test
    public void galaxyS25StillRejectsEdgeVariant() {
        assertFalse(
                matcher.matchesFullTitle(
                        "Samsung Galaxy S25 Edge 12/256GB",
                        samsungModel("Galaxy S25")
                )
        );
    }

    @Test
    public void galaxyS25StillRejectsDifferentGeneration() {
        assertFalse(
                matcher.matchesFullTitle(
                        "Samsung Galaxy S24 SM-S921 8/256GB",
                        samsungModel("Galaxy S25")
                )
        );
    }

    @Test
    public void galaxyS25PlusAcceptsItsTechnicalProductCode() {
        assertTrue(
                matcher.matchesFullTitle(
                        "Samsung Galaxy S25+ SM-S936 12/256GB",
                        samsungModel("Galaxy S25+")
                )
        );
    }

    private BotConfigurationDto samsungModel(String model) {
        BotConfigurationDto configuration = new BotConfigurationDto();
        configuration.setTargetMode("VINTED_MODEL");
        configuration.setBrand("Samsung");
        configuration.setModel(model);
        return configuration;
    }
}
