package pl.flipbot.playwright.worker;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;

public class WorkerMemoryPressureControllerTest {

    private static final long GIB =
            1024L * 1024L * 1024L;

    @Test
    public void coldStartCapsInitialTenWorkerLaunchStormAtFour() {
        WorkerMemoryPressureController controller =
                controller(32, 7, true);

        assertEquals(
                4,
                controller.targetSlots(10)
        );
    }

    @Test
    public void emergencyPressureKeepsColdStartAtFourWorkers() {
        WorkerMemoryPressureController controller =
                controller(32, 1, true);

        assertEquals(
                4,
                controller.targetSlots(10)
        );
    }

    @Test
    public void highPressureCutsRecoveredCapacityToSix() {
        PressureFixture fixture = recoveredFixture();

        fixture.snapshot().set(snapshot(32, 3));

        assertEquals(
                6,
                fixture.controller().targetSlots(10)
        );
    }

    @Test
    public void elevatedPressureCutsRecoveredCapacityToEight() {
        PressureFixture fixture = recoveredFixture();

        fixture.snapshot().set(snapshot(32, 6));

        assertEquals(
                8,
                fixture.controller().targetSlots(10)
        );
    }

    @Test
    public void recoveryRestoresOnlyOneSlotAfterHoldWindow() {
        AtomicLong clock = new AtomicLong();

        WorkerMemoryPressureController.MemorySnapshot emergency =
                snapshot(32, 1);

        WorkerMemoryPressureController.MemorySnapshot healthy =
                snapshot(32, 10);

        AtomicReference<WorkerMemoryPressureController.MemorySnapshot> snapshot =
                new AtomicReference<>(emergency);

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
    public void coldStartStillRespectsSmallerRequestedCapacity() {
        WorkerMemoryPressureController controller =
                controller(32, 16, true);

        assertEquals(
                2,
                controller.targetSlots(2)
        );
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

    private PressureFixture recoveredFixture() {
        AtomicLong clock = new AtomicLong();
        AtomicReference<WorkerMemoryPressureController.MemorySnapshot> snapshot =
                new AtomicReference<>(snapshot(32, 12));

        WorkerMemoryPressureController controller =
                WorkerMemoryPressureController.forTest(
                        10,
                        true,
                        snapshot::get,
                        clock::get
                );

        assertEquals(4, controller.targetSlots(10));

        for (int expected = 5; expected <= 10; expected++) {
            clock.addAndGet(
                    WorkerMemoryPressureController.RECOVERY_HOLD_NANOS
            );
            assertEquals(expected, controller.targetSlots(10));
        }

        return new PressureFixture(
                controller,
                snapshot
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
                () -> snapshot(totalGiB, freeGiB),
                System::nanoTime
        );
    }

    private WorkerMemoryPressureController.MemorySnapshot snapshot(
            long totalGiB,
            long freeGiB
    ) {
        return new WorkerMemoryPressureController.MemorySnapshot(
                totalGiB * GIB,
                freeGiB * GIB
        );
    }

    private record PressureFixture(
            WorkerMemoryPressureController controller,
            AtomicReference<WorkerMemoryPressureController.MemorySnapshot> snapshot
    ) {
    }
}
