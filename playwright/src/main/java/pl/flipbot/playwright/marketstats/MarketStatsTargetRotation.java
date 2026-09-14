package pl.flipbot.playwright.marketstats;

import java.util.ArrayList;
import java.util.List;

final class MarketStatsTargetRotation {

    private MarketStatsTargetRotation() {
    }

    static int normalizeStartIndex(int requestedStartIndex, int size) {
        if (size <= 0) {
            return 0;
        }

        return Math.floorMod(requestedStartIndex, size);
    }

    static int nextIndex(int currentIndex, int size) {
        if (size <= 0 || currentIndex < 0) {
            return 0;
        }

        return (currentIndex + 1) % size;
    }

    static <T> List<T> rotate(List<T> values, int requestedStartIndex) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }

        int startIndex = normalizeStartIndex(
                requestedStartIndex,
                values.size()
        );

        if (startIndex == 0) {
            return List.copyOf(values);
        }

        List<T> rotated = new ArrayList<>(values.size());
        rotated.addAll(values.subList(startIndex, values.size()));
        rotated.addAll(values.subList(0, startIndex));
        return List.copyOf(rotated);
    }
}
