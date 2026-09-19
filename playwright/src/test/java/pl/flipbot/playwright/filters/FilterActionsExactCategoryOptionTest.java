package pl.flipbot.playwright.filters;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FilterActionsExactCategoryOptionTest {

    @Test
    public void exactCategoryTextAllowsWhitespaceAndCaseNormalization() {
        assertTrue(
                FilterActions.exactFilterOptionTextMatches(
                        "Telefony komórkowe",
                        "  telefony   komórkowe  "
                )
        );

        assertTrue(
                FilterActions.exactFilterOptionTextMatches(
                        "Tablety",
                        "Tablety"
                )
        );
    }

    @Test
    public void exactCategoryTextRejectsParentAndSimilarLabels() {
        assertFalse(
                FilterActions.exactFilterOptionTextMatches(
                        "Telefony komórkowe",
                        "Telefony komórkowe i komunikacja"
                )
        );

        assertFalse(
                FilterActions.exactFilterOptionTextMatches(
                        "Tablety",
                        "Tablety i akcesoria"
                )
        );

        assertFalse(
                FilterActions.exactFilterOptionTextMatches(
                        "Telefony komórkowe",
                        "Telefony komórkowe 1234"
                )
        );
    }
}
