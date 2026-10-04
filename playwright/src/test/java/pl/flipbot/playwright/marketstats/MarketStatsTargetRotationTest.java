package pl.flipbot.playwright.marketstats;

import org.junit.Test;
import pl.flipbot.playwright.marketstats.dto.MarketStatsTargetDto;

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
    public void returnsBoundedBatchAcrossWrapAroundWithoutDuplicates() {
        assertEquals(
                List.of("i", "j", "a"),
                MarketStatsTargetRotation.batch(
                        List.of("a", "b", "c", "d", "e", "f", "g", "h", "i", "j"),
                        8,
                        3
                )
        );

        assertEquals(
                List.of("b"),
                MarketStatsTargetRotation.batch(
                        List.of("a", "b", "c", "d", "e", "f", "g", "h", "i", "j"),
                        1,
                        1
                )
        );
    }

    @Test
    public void resumableClientKeepsFrozenOrderAcrossBackendReordering() {
        MarketStatsTargetDto a = target(1L, "A");
        MarketStatsTargetDto b = target(2L, "B");
        MarketStatsTargetDto c = target(3L, "C");

        assertEquals(
                List.of(c, a, b),
                ResumableMarketStatsApiClient.applyPreferredTargetOrder(
                        List.of(a, b, c),
                        List.of(3L, 1L, 2L)
                )
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonPositiveBatchSize() {
        MarketStatsTargetRotation.batch(
                List.of("a"),
                0,
                0
        );
    }

    private MarketStatsTargetDto target(
            Long id,
            String model
    ) {
        return new MarketStatsTargetDto(
                id,
                "Brand",
                model,
                "VINTED_MODEL",
                List.of("Elektronika"),
                true,
                null,
                null,
                1
        );
    }

    @Test
    public void emptyTargetListUsesZeroIndex() {
        assertEquals(0, MarketStatsTargetRotation.normalizeStartIndex(5, 0));
        assertEquals(0, MarketStatsTargetRotation.nextIndex(2, 0));
        assertEquals(List.of(), MarketStatsTargetRotation.rotate(List.of(), 2));
        assertEquals(List.of(), MarketStatsTargetRotation.batch(List.of(), 2, 3));
    }
}
