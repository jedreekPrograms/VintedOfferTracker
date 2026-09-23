package pl.flipbot.playwright.worker;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class WorkerSlotCapacityPlanTest {

    @Test
    public void scalesTenRunningSlotsDownToThree() {
        WorkerSlotCapacityPlan plan = WorkerSlotCapacityPlan.between(10, 3);

        assertEquals(0, plan.startCount());
        assertEquals(7, plan.retireCount());
    }

    @Test
    public void scalesThreeRunningSlotsUpToTen() {
        WorkerSlotCapacityPlan plan = WorkerSlotCapacityPlan.between(3, 10);

        assertEquals(7, plan.startCount());
        assertEquals(0, plan.retireCount());
    }

    @Test
    public void keepsMatchingCapacityUnchanged() {
        WorkerSlotCapacityPlan plan = WorkerSlotCapacityPlan.between(3, 3);

        assertEquals(0, plan.startCount());
        assertEquals(0, plan.retireCount());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNegativeCounts() {
        WorkerSlotCapacityPlan.between(-1, 3);
    }
}
