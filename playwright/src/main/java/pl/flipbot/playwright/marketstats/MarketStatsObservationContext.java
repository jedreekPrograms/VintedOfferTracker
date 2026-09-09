package pl.flipbot.playwright.marketstats;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

final class MarketStatsObservationContext {

    private static final ThreadLocal<State> CURRENT = new ThreadLocal<>();

    private MarketStatsObservationContext() {
    }

    static void begin(
            Long modelId,
            Collection<String> knownListingIds,
            boolean baselineComplete
    ) {
        Set<String> known = new LinkedHashSet<>();

        if (knownListingIds != null) {
            for (String listingId : knownListingIds) {
                if (listingId != null && !listingId.isBlank()) {
                    known.add(listingId.trim());
                }
            }
        }

        CURRENT.set(
                new State(
                        modelId,
                        Set.copyOf(known),
                        baselineComplete,
                        new LinkedHashSet<>(),
                        new LinkedHashMap<>()
                )
        );
    }

    static boolean claimPublicationResolution(String listingId) {
        State state = CURRENT.get();

        if (state == null
                || !state.baselineComplete()
                || listingId == null
                || listingId.isBlank()) {
            return false;
        }

        String normalized = listingId.trim();

        if (state.knownListingIds().contains(normalized)) {
            return false;
        }

        return state.attemptedListingIds().add(normalized);
    }

    static void recordPublishedAt(
            String listingId,
            LocalDateTime publishedAt
    ) {
        State state = CURRENT.get();

        if (state == null
                || listingId == null
                || listingId.isBlank()
                || publishedAt == null) {
            return;
        }

        state.publishedAtByListingId().put(
                listingId.trim(),
                publishedAt
        );
    }

    static Map<String, LocalDateTime> resolvedFor(
            Long modelId,
            Collection<String> listingIds
    ) {
        State state = CURRENT.get();

        if (state == null
                || modelId == null
                || !modelId.equals(state.modelId())
                || listingIds == null
                || listingIds.isEmpty()) {
            return Map.of();
        }

        Set<String> acceptedIds = new LinkedHashSet<>();

        for (String listingId : listingIds) {
            if (listingId != null && !listingId.isBlank()) {
                acceptedIds.add(listingId.trim());
            }
        }

        Map<String, LocalDateTime> result = new LinkedHashMap<>();

        for (Map.Entry<String, LocalDateTime> entry
                : state.publishedAtByListingId().entrySet()) {
            if (acceptedIds.contains(entry.getKey())) {
                result.put(entry.getKey(), entry.getValue());
            }
        }

        return Map.copyOf(result);
    }

    static void clear(Long modelId) {
        State state = CURRENT.get();

        if (state == null
                || modelId == null
                || modelId.equals(state.modelId())) {
            CURRENT.remove();
        }
    }

    private record State(
            Long modelId,
            Set<String> knownListingIds,
            boolean baselineComplete,
            Set<String> attemptedListingIds,
            Map<String, LocalDateTime> publishedAtByListingId
    ) {
    }
}
