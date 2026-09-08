package pl.flipbot.playwright.filters.category;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CategoryNavigatorSelectorTest {

    @Test
    public void exactVisibleTextSelectorUsesExactVisiblePlaywrightMatching() {
        assertEquals(
                "*:text-is(\"Tablety, czytniki e-booków i akcesoria\"):visible >> nth=0",
                CategoryNavigator.exactVisibleTextSelector(
                        "Tablety, czytniki e-booków i akcesoria"
                )
        );
    }

    @Test
    public void exactVisibleTextSelectorEscapesQuotesAndBackslashes() {
        assertEquals(
                "*:text-is(\"A \\\"B\\\" \\\\ C\"):visible >> nth=0",
                CategoryNavigator.exactVisibleTextSelector(
                        "A \"B\" \\ C"
                )
        );
    }
}
