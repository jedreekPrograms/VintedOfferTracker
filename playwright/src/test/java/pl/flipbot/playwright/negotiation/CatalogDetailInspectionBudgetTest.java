package pl.flipbot.playwright.negotiation;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class CatalogDetailInspectionBudgetTest {

    @Test
    public void allowsExactlyTheConfiguredNumberOfLiveDetailRequests() {
        CatalogDetailInspectionBudget budget =
                new CatalogDetailInspectionBudget(25);

        for (int request = 1; request <= 25; request++) {
            assertTrue(budget.tryAcquire());
            assertEquals(request, budget.used());
            assertEquals(25 - request, budget.remaining());
        }

        assertTrue(budget.exhausted());
        assertFalse(budget.tryAcquire());
        assertFalse(budget.tryAcquire());
        assertEquals(25, budget.used());
        assertEquals(0, budget.remaining());
    }

    @Test
    public void oneInstanceSharesCapacityAcrossIndependentConsumers() {
        CatalogDetailInspectionBudget budget =
                new CatalogDetailInspectionBudget(3);

        assertTrue(budget.tryAcquire()); // MAIN pre-check
        assertTrue(budget.tryAcquire()); // MAIN final verification
        assertTrue(budget.tryAcquire()); // ADDITIONAL final verification

        assertFalse(budget.tryAcquire()); // next product is deferred
        assertEquals(3, budget.used());
    }

    @Test
    public void rejectsNonPositiveLimits() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new CatalogDetailInspectionBudget(0)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new CatalogDetailInspectionBudget(-1)
        );
    }
}
