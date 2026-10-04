package pl.flipbot.playwright.worker;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class WorkerManagerSizingTest {

    @Test
    public void livePreviewReplacesOneGenericWorkerSlot() {
        assertEquals(
                3,
                WorkerManager.genericWorkerSlotsRequested(
                        4,
                        1,
                        10
                )
        );
    }

    @Test
    public void allBotsInLivePreviewNeedNoGenericWorkerSlots() {
        assertEquals(
                0,
                WorkerManager.genericWorkerSlotsRequested(
                        3,
                        3,
                        10
                )
        );
    }

    @Test
    public void configuredMaximumStillCapsGenericWorkers() {
        assertEquals(
                6,
                WorkerManager.genericWorkerSlotsRequested(
                        12,
                        2,
                        6
                )
        );
    }
}
