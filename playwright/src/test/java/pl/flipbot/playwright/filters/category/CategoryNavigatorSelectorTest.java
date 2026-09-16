package pl.flipbot.playwright.filters.category;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class CategoryNavigatorSelectorTest {

    @Test
    public void buildsExactRoleSelectorForCategoryName() {
        assertEquals(
                "role=button[name=\"Elektronika\"]",
                CategoryNavigator.exactCategoryRoleSelector("Elektronika")
        );

        assertEquals(
                "role=button[name=\"Galaxy S25\"]",
                CategoryNavigator.exactCategoryRoleSelector("Galaxy S25")
        );
    }

    @Test
    public void escapesQuotesAndBackslashesInCategoryName() {
        assertEquals(
                "role=button[name=\"A \\\"B\\\" \\\\ C\"]",
                CategoryNavigator.exactCategoryRoleSelector("A \"B\" \\ C")
        );
    }

    @Test
    public void rejectsBlankCategoryNames() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CategoryNavigator.exactCategoryRoleSelector("   ")
        );
    }
}
