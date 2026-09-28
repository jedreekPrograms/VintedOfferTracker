package pl.flipbot.playwright.worker;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;

public class WorkerMemoryPressureControllerTest {

    private static final long GIB =
            1024L * 1024L * 1024L;

    @Test
    public void emergencyPressureCutsTenWorkersToFour() {
        WorkerMemoryPressureController controller =
                controller(32, 1, true);

        assertEquals(
                4,
                controller.targetSlots(10)
        );
    }

    @Test
    public void highPressureCutsTenWorkersToSix() {
        WorkerMemoryPressureController controller =
                controller(32, 3, true);

        assertEquals(
                6,
                controller.targetSlots(10)
        );
    }

    @Test
    public void elevatedPressureCutsTenWorkersToEight() {
        WorkerMemoryPressureController controller =
                controller(32, 6, true);

        assertEquals(
                8,
                controller.targetSlots(10)
        );
    }

    @Test
    public void recoveryRestoresOnlyOneSlotAfterHoldWindow() {
        AtomicLong clock = new AtomicLong();

        WorkerMemoryPressureController.MemorySnapshot emergency =
                new WorkerMemoryPressureController.MemorySnapshot(
                        32 * GIB,
                        1 * GIB
                );

        WorkerMemoryPressureController.MemorySnapshot healthy =
                new WorkerMemoryPressureController.MemorySnapshot(
                        32 * GIB,
                        10 * GIB
                );

        java.util.concurrent.atomic.AtomicReference<
                WorkerMemoryPressureController.MemorySnapshot
                > snapshot =
                new java.util.concurrent.atomic.AtomicReference<>(
                        emergency
                );

        WorkerMemoryPressureController controller =
                WorkerMemoryPressureController.forTest(
                        10,
                        true,
                        snapshot::get,
                        clock::get
                );

        assertEquals(4, controller.targetSlots(10));

        snapshot.set(healthy);

        assertEquals(4, controller.targetSlots(10));

        clock.addAndGet(
                WorkerMemoryPressureController.RECOVERY_HOLD_NANOS
        );

        assertEquals(5, controller.targetSlots(10));
    }

    @Test
    public void disabledControllerKeepsConfiguredParallelism() {
        WorkerMemoryPressureController controller =
                controller(32, 1, false);

        assertEquals(
                10,
                controller.targetSlots(10)
        );
    }

    private WorkerMemoryPressureController controller(
            long totalGiB,
            long freeGiB,
            boolean enabled
    ) {
        return WorkerMemoryPressureController.forTest(
                10,
                enabled,
                () -> new WorkerMemoryPressureController.MemorySnapshot(
                        totalGiB * GIB,
                        freeGiB * GIB
                ),
                System::nanoTime
        );
    }
}
