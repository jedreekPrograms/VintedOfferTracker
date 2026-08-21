package pl.flipbot.playwright.filters;

import org.junit.Test;

import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class FilterActionsModelOptionTest {

    @Test
    public void exactModelPatternAcceptsOnlyTheRequestedModel() {
        var pattern = FilterActions.exactModelOptionPattern("Galaxy S25");

        assertTrue(pattern.matcher("Galaxy S25").matches());
        assertTrue(pattern.matcher("  Galaxy S25  ").matches());

        assertFalse(pattern.matcher("Galaxy S25 Edge").matches());
        assertFalse(pattern.matcher("Galaxy S25 Ultra").matches());
        assertFalse(pattern.matcher("Galaxy S25 FE").matches());
        assertFalse(pattern.matcher("Galaxy S25+").matches());
        assertFalse(pattern.matcher("Galaxy S24").matches());
    }

    @Test
    public void exactModelPatternIsCaseInsensitive() {
        var pattern = FilterActions.exactModelOptionPattern("Galaxy Tab S11 Ultra");

        assertTrue(pattern.matcher("GALAXY TAB S11 ULTRA").matches());
        assertFalse(pattern.matcher("Galaxy Tab S11").matches());
    }

    @Test
    public void exactModelPatternUsesOnlyPlaywrightSupportedFlags() {
        var pattern = FilterActions.exactModelOptionPattern("Galaxy S25");

        assertEquals(Pattern.CASE_INSENSITIVE, pattern.flags());
    }

    @Test
    public void modelCollectionIdIsDerivedOnlyFromExpectedVintedTestId() {
        assertEquals(
                "9976",
                FilterActions.modelCollectionIdFromTestId(
                        "selectable-item-brand_collection-9976"
                )
        );
        assertEquals(
                "9976",
                FilterActions.modelCollectionIdFromTestId(
                        "selectable-item-brand_collection-9976--title"
                )
        );

        assertNull(FilterActions.modelCollectionIdFromTestId("9976"));
        assertNull(
                FilterActions.modelCollectionIdFromTestId(
                        "selectable-item-brand_collection-Galaxy-S25"
                )
        );
        assertNull(
                FilterActions.modelCollectionIdFromTestId(
                        "selectable-item-brand_collection-9976--unexpected-child"
                )
        );
    }

    @Test
    public void visibleModelMatcherAcceptsExactLabelWithCountMetadata() {
        assertTrue(
                FilterActions.exactVisibleModelLabelMatches(
                        "Galaxy S25",
                        "Galaxy S25\n123 przedmioty"
                )
        );

        assertTrue(
                FilterActions.exactVisibleModelLabelMatches(
                        "Galaxy S25",
                        "  GALAXY S25  \n  123 przedmiotów  "
                )
        );

        assertTrue(
                FilterActions.exactVisibleModelLabelMatches(
                        "Galaxy S25",
                        "Galaxy S25 123 przedmioty"
                )
        );
    }

    @Test
    public void visibleModelMatcherRejectsEveryS25Variant() {
        assertFalse(
                FilterActions.exactVisibleModelLabelMatches(
                        "Galaxy S25",
                        "Galaxy S25 Edge\n50 przedmiotów"
                )
        );
        assertFalse(
                FilterActions.exactVisibleModelLabelMatches(
                        "Galaxy S25",
                        "Galaxy S25 Ultra\n50 przedmiotów"
                )
        );
        assertFalse(
                FilterActions.exactVisibleModelLabelMatches(
                        "Galaxy S25",
                        "Galaxy S25 FE\n50 przedmiotów"
                )
        );
        assertFalse(
                FilterActions.exactVisibleModelLabelMatches(
                        "Galaxy S25",
                        "Galaxy S25+\n50 przedmiotów"
                )
        );
    }

    @Test
    public void visibleModelMatcherRejectsHighlightedBaseNameInsideVariantRow() {
        assertFalse(
                FilterActions.exactVisibleModelLabelMatches(
                        "Galaxy S25",
                        "Galaxy S25\nEdge\n50 przedmiotów"
                )
        );
        assertFalse(
                FilterActions.exactVisibleModelLabelMatches(
                        "Galaxy S25",
                        "Galaxy S25\nUltra\n50 przedmiotów"
                )
        );
        assertFalse(
                FilterActions.exactVisibleModelLabelMatches(
                        "Galaxy S25",
                        "Galaxy S25\nFE\n50 przedmiotów"
                )
        );
    }

    @Test
    public void visibleModelMatcherDoesNotTreatAProductSuffixAsCountMetadata() {
        assertFalse(
                FilterActions.exactVisibleModelLabelMatches(
                        "Galaxy S25",
                        "Galaxy S25 5G"
                )
        );
    }
}
