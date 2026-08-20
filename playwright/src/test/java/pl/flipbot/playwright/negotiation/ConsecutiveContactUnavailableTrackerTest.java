package pl.flipbot.playwright.negotiation;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConsecutiveContactUnavailableTrackerTest {

    private ConsecutiveContactUnavailableTracker tracker;

    @Before
    public void setUp() {
        ConsecutiveContactUnavailableTracker.clearAllForTests();
        tracker = new ConsecutiveContactUnavailableTracker();
    }

    @After
    public void tearDown() {
        ConsecutiveContactUnavailableTracker.clearAllForTests();
    }

    @Test
    public void closesOnlyAfterThreeConsecutiveSuspicions() {
        int first = tracker.recordSuspected(4L, "9705899581");
        int second = tracker.recordSuspected(4L, "9705899581");
        int third = tracker.recordSuspected(4L, "9705899581");

        assertFalse(tracker.shouldClose(first));
        assertFalse(tracker.shouldClose(second));
        assertTrue(tracker.shouldClose(third));
    }

    @Test
    public void successfulContactCheckResetsSuspicionCounter() {
        tracker.recordSuspected(4L, "9705899581");
        tracker.recordSuspected(4L, "9705899581");

        tracker.clear(4L, "9705899581");

        int afterReset = tracker.recordSuspected(4L, "9705899581");

        assertFalse(tracker.shouldClose(afterReset));
    }

    @Test
    public void countersAreIndependentPerBotAndListing() {
        tracker.recordSuspected(4L, "A");
        tracker.recordSuspected(4L, "A");

        int otherListing = tracker.recordSuspected(4L, "B");
        int otherBot = tracker.recordSuspected(3L, "A");

        assertFalse(tracker.shouldClose(otherListing));
        assertFalse(tracker.shouldClose(otherBot));
    }
}
