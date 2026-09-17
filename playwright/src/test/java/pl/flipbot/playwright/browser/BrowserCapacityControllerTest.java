package pl.flipbot.playwright.browser;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

public class BrowserCapacityControllerTest {
    private static final long GIB = 1024L * BrowserCapacityController.MIB;
    private final AtomicLong time = new AtomicLong();
    private final AtomicReference<BrowserCapacityController.Resources> resources = new AtomicReference<>(
            new BrowserCapacityController.Resources(32 * GIB, 20 * GIB, 0.25));
    private final BrowserCapacityController capacity = new BrowserCapacityController(true, 10, resources::get, time::get);

    private BrowserCapacityController.Permit at(long milliseconds) {
        time.set(milliseconds);
        return capacity.acquire();
    }

    @Test public void growsPastThreeOnlyAfterSustainedHeadroomAndDemand() {
        at(0); at(2_000); at(4_000);
        time.set(6_000);
        assertThrows(BrowserCapacityUnavailableException.class, capacity::acquire);
        assertEquals(3, capacity.targetConcurrency());
        at(30_001);
        assertEquals(4, capacity.activeCount());
        assertEquals(4, capacity.targetConcurrency());
    }

    @Test public void memoryPressureDefersNewWorkWithoutCancellingExistingJobs() {
        var first = at(0); var second = at(2_000);
        resources.set(new BrowserCapacityController.Resources(32 * GIB, GIB, 0.25));
        time.set(4_000);
        assertThrows(BrowserCapacityUnavailableException.class, capacity::acquire);
        assertEquals(2, capacity.activeCount());
        first.close(); first.close();
        assertEquals(1, capacity.activeCount());
        second.close();
        assertEquals(0, capacity.activeCount());
        assertThrows(BrowserCapacityUnavailableException.class, capacity::acquire);
    }

    @Test public void launchReservationsProtectAgainstLaggingMemorySamples() {
        resources.set(new BrowserCapacityController.Resources(16 * GIB, 4 * GIB, 0.25));
        at(0); at(2_000);
        resources.set(new BrowserCapacityController.Resources(16 * GIB, 3 * GIB, 0.25));
        time.set(4_000);
        assertThrows(BrowserCapacityUnavailableException.class, capacity::acquire);
        at(12_001);
        assertEquals(3, capacity.activeCount());
    }

    @Test public void cpuPressureBlocksLaunchAndResetsGrowthWindow() {
        at(0);
        resources.set(new BrowserCapacityController.Resources(32 * GIB, 20 * GIB, 0.95));
        time.set(31_000);
        assertThrows(BrowserCapacityUnavailableException.class, capacity::acquire);
        assertEquals(2, capacity.targetConcurrency());
        assertEquals(1, capacity.activeCount());
    }

    @Test public void marketplaceBackoffReducesParallelismAndPreventsEarlyRegrowth() {
        var first = at(0); var second = at(2_000); var third = at(4_000); var fourth = at(30_001);
        capacity.marketplaceBackoff(600_000L);
        assertEquals(2, capacity.targetConcurrency());
        assertEquals(4, capacity.activeCount());
        third.close(); fourth.close();
        time.set(61_000);
        assertThrows(BrowserCapacityUnavailableException.class, capacity::acquire);
        assertEquals(2, capacity.targetConcurrency());
        first.close(); second.close();
        at(65_000); at(67_000);
        time.set(100_000);
        assertThrows(BrowserCapacityUnavailableException.class, capacity::acquire);
        assertEquals(2, capacity.targetConcurrency());
    }

    @Test public void unavailableMeasurementsNeverEnableHighParallelism() {
        resources.set(new BrowserCapacityController.Resources(-1, -1, -1));
        at(0); at(2_000); at(4_000);
        time.set(60_000);
        assertThrows(BrowserCapacityUnavailableException.class, capacity::acquire);
        assertEquals(3, capacity.targetConcurrency());
    }

    @Test public void concurrentAdmissionsCannotAllUseTheSameFreeMemorySample() throws Exception {
        var executor = Executors.newFixedThreadPool(12);
        try {
            List<java.util.concurrent.Callable<BrowserCapacityController.Permit>> attempts = new ArrayList<>();
            for (int i = 0; i < 12; i++) attempts.add(() -> {
                try { return capacity.acquire(); }
                catch (BrowserCapacityUnavailableException ignored) { return null; }
            });
            int admitted = 0;
            for (var result : executor.invokeAll(attempts)) if (result.get() != null) admitted++;
            assertEquals(1, admitted);
            assertEquals(1, capacity.activeCount());
        } finally { executor.shutdownNow(); }
    }

    @Test public void explicitCompatibilityModeLeavesLegacyLimitsInCharge() {
        var legacy = new BrowserCapacityController(false, 1, () -> { throw new AssertionError(); }, time::get);
        try (var first = legacy.acquire(); var second = legacy.acquire()) {
            assertEquals(2, legacy.activeCount());
        }
        assertEquals(0, legacy.activeCount());
    }
}
