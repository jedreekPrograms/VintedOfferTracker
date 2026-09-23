package pl.flipbot.analytics;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class AnalyticsMath {

    private static final int SCALE = 2;
    private static final MathContext MATH_CONTEXT = MathContext.DECIMAL64;

    private AnalyticsMath() {
    }

    public static PriceSummary summarize(List<BigDecimal> rawValues) {
        List<BigDecimal> values = normalize(rawValues);

        if (values.isEmpty()) {
            return PriceSummary.empty();
        }

        BigDecimal sum = values.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal average = sum.divide(
                BigDecimal.valueOf(values.size()),
                SCALE,
                RoundingMode.HALF_UP
        );

        BigDecimal varianceSum = BigDecimal.ZERO;
        for (BigDecimal value : values) {
            BigDecimal delta = value.subtract(average);
            varianceSum = varianceSum.add(delta.multiply(delta));
        }

        BigDecimal variance = varianceSum.divide(
                BigDecimal.valueOf(values.size()),
                8,
                RoundingMode.HALF_UP
        );

        BigDecimal standardDeviation = BigDecimal.valueOf(
                        Math.sqrt(variance.doubleValue())
                )
                .setScale(SCALE, RoundingMode.HALF_UP);

        return new PriceSummary(
                values.size(),
                average,
                percentile(values, 0.50),
                percentile(values, 0.25),
                percentile(values, 0.75),
                standardDeviation,
                values.getFirst().setScale(SCALE, RoundingMode.HALF_UP),
                values.getLast().setScale(SCALE, RoundingMode.HALF_UP)
        );
    }

    public static List<HistogramBucket> histogram(
            List<BigDecimal> rawValues,
            int requestedBuckets
    ) {
        List<BigDecimal> values = normalize(rawValues);

        if (values.isEmpty()) {
            return List.of();
        }

        int bucketCount = Math.max(
                1,
                Math.min(requestedBuckets, 20)
        );

        BigDecimal min = values.getFirst();
        BigDecimal max = values.getLast();

        if (min.compareTo(max) == 0) {
            return List.of(
                    new HistogramBucket(
                            min.setScale(SCALE, RoundingMode.HALF_UP),
                            max.setScale(SCALE, RoundingMode.HALF_UP),
                            values.size()
                    )
            );
        }

        BigDecimal width = max.subtract(min)
                .divide(
                        BigDecimal.valueOf(bucketCount),
                        8,
                        RoundingMode.HALF_UP
                );

        int[] counts = new int[bucketCount];

        for (BigDecimal value : values) {
            BigDecimal relative = value.subtract(min);
            int index = relative.divide(
                            width,
                            0,
                            RoundingMode.FLOOR
                    )
                    .intValue();

            if (index >= bucketCount) {
                index = bucketCount - 1;
            }

            counts[index]++;
        }

        List<HistogramBucket> result = new ArrayList<>();

        for (int index = 0; index < bucketCount; index++) {
            BigDecimal from = min.add(
                    width.multiply(BigDecimal.valueOf(index), MATH_CONTEXT)
            );
            BigDecimal to = index == bucketCount - 1
                    ? max
                    : min.add(
                            width.multiply(
                                    BigDecimal.valueOf(index + 1L),
                                    MATH_CONTEXT
                            )
                    );

            result.add(
                    new HistogramBucket(
                            from.setScale(SCALE, RoundingMode.HALF_UP),
                            to.setScale(SCALE, RoundingMode.HALF_UP),
                            counts[index]
                    )
            );
        }

        return List.copyOf(result);
    }

    private static List<BigDecimal> normalize(List<BigDecimal> rawValues) {
        if (rawValues == null || rawValues.isEmpty()) {
            return List.of();
        }

        return rawValues.stream()
                .filter(value -> value != null && value.signum() > 0)
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private static BigDecimal percentile(
            List<BigDecimal> sortedValues,
            double fraction
    ) {
        if (sortedValues.isEmpty()) {
            return null;
        }

        if (sortedValues.size() == 1) {
            return sortedValues.getFirst()
                    .setScale(SCALE, RoundingMode.HALF_UP);
        }

        double position = fraction * (sortedValues.size() - 1);
        int lower = (int) Math.floor(position);
        int upper = (int) Math.ceil(position);

        if (lower == upper) {
            return sortedValues.get(lower)
                    .setScale(SCALE, RoundingMode.HALF_UP);
        }

        BigDecimal lowerValue = sortedValues.get(lower);
        BigDecimal upperValue = sortedValues.get(upper);
        BigDecimal weight = BigDecimal.valueOf(position - lower);

        return lowerValue
                .add(
                        upperValue.subtract(lowerValue)
                                .multiply(weight, MATH_CONTEXT)
                )
                .setScale(SCALE, RoundingMode.HALF_UP);
    }

    public record PriceSummary(
            int count,
            BigDecimal average,
            BigDecimal median,
            BigDecimal p25,
            BigDecimal p75,
            BigDecimal standardDeviation,
            BigDecimal min,
            BigDecimal max
    ) {
        static PriceSummary empty() {
            return new PriceSummary(
                    0,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }
    }

    public record HistogramBucket(
            BigDecimal from,
            BigDecimal to,
            int count
    ) {
    }
}
