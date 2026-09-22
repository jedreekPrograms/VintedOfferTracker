package pl.flipbot.analytics;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AnalyticsMathTest {

    @Test
    void calculatesStableDescriptivePriceStatistics() {
        AnalyticsMath.PriceSummary summary = AnalyticsMath.summarize(
                List.of(
                        new BigDecimal("100"),
                        new BigDecimal("200"),
                        new BigDecimal("300"),
                        new BigDecimal("400")
                )
        );

        assertEquals(4, summary.count());
        assertEquals(new BigDecimal("250.00"), summary.average());
        assertEquals(new BigDecimal("250.00"), summary.median());
        assertEquals(new BigDecimal("175.00"), summary.p25());
        assertEquals(new BigDecimal("325.00"), summary.p75());
        assertEquals(new BigDecimal("100.00"), summary.min());
        assertEquals(new BigDecimal("400.00"), summary.max());
    }

    @Test
    void emptyPriceSetReturnsNullMetricsInsteadOfInventedZeroes() {
        AnalyticsMath.PriceSummary summary =
                AnalyticsMath.summarize(List.of());

        assertEquals(0, summary.count());
        assertNull(summary.average());
        assertNull(summary.median());
        assertNull(summary.standardDeviation());
    }

    @Test
    void histogramKeepsEveryPriceExactlyOnce() {
        List<AnalyticsMath.HistogramBucket> buckets =
                AnalyticsMath.histogram(
                        List.of(
                                new BigDecimal("100"),
                                new BigDecimal("150"),
                                new BigDecimal("200"),
                                new BigDecimal("250"),
                                new BigDecimal("300")
                        ),
                        4
                );

        int total = buckets.stream()
                .mapToInt(AnalyticsMath.HistogramBucket::count)
                .sum();

        assertEquals(5, total);
    }
}
