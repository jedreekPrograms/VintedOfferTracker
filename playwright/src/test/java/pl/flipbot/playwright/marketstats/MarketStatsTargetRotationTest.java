package pl.flipbot.playwright.marketstats;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class MarketStatsTargetRotationTest {

    @Test
    public void rotatesTargetsFromRequestedResumeIndexAndWrapsAround() {
        assertEquals(
                List.of("c", "d", "a", "b"),
                MarketStatsTargetRotation.rotate(
                        List.of("a", "b", "c", "d"),
                        2
                )
        );
    }

    @Test
    public void normalizesOutOfRangeResumeIndex() {
        assertEquals(
                List.of("b", "c", "a"),
                MarketStatsTargetRotation.rotate(
                        List.of("a", "b", "c"),
                        4
                )
        );
    }

    @Test
    public void advancesToTheFollowingTargetAndWrapsAtTheEnd() {
        assertEquals(3, MarketStatsTargetRotation.nextIndex(2, 4));
        assertEquals(0, MarketStatsTargetRotation.nextIndex(3, 4));
    }

    @Test
    public void emptyTargetListUsesZeroIndex() {
        assertEquals(0, MarketStatsTargetRotation.normalizeStartIndex(5, 0));
        assertEquals(0, MarketStatsTargetRotation.nextIndex(2, 0));
        assertEquals(List.of(), MarketStatsTargetRotation.rotate(List.of(), 2));
    }
}
