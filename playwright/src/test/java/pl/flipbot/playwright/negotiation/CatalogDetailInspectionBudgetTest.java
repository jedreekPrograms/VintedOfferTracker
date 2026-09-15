package pl.flipbot.playwright.negotiation;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CatalogDetailInspectionBudgetTest {

    @Test
    public void doesNotAllowMoreThanConfiguredNumberOfLiveDetailRequests() {
        CatalogDetailInspectionBudget budget =
                new CatalogDetailInspectionBudget(25);

        for (int index = 0; index < 25; index++) {
            assertTrue(budget.tryAcquire());
        }

        assertFalse(budget.tryAcquire());
        assertEquals(25, budget.used());
        assertEquals(0, budget.remaining());
        assertTrue(budget.exhausted());
    }

    @Test
    public void oneInstanceIsSharedAcrossIndependentConsumers() {
        CatalogDetailInspectionBudget budget =
                new CatalogDetailInspectionBudget(3);

        assertTrue(budget.tryAcquire());
        assertTrue(budget.tryAcquire());
        assertEquals(1, budget.remaining());

        assertTrue(budget.tryAcquire());
        assertFalse(budget.tryAcquire());
        assertEquals(3, budget.used());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsZeroLimit() {
        new CatalogDetailInspectionBudget(0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNegativeLimit() {
        new CatalogDetailInspectionBudget(-1);
    }
}
