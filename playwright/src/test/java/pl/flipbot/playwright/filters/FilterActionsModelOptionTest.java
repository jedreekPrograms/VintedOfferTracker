package pl.flipbot.playwright.filters;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
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
}
