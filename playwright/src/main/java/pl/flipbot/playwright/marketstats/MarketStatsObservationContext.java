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
            Collection<String> missingPublicationListingIds,
            boolean refreshAllPublicationTimes
    ) {
        CURRENT.set(
                new State(
                        modelId,
                        Set.copyOf(normalizeIds(knownListingIds)),
                        Set.copyOf(normalizeIds(missingPublicationListingIds)),
                        refreshAllPublicationTimes,
                        new LinkedHashSet<>(),
                        new LinkedHashMap<>()
                )
        );
    }

    static boolean claimPublicationResolution(String listingId) {
        State state = CURRENT.get();

        if (state == null || listingId == null || listingId.isBlank()) {
            return false;
        }

        String normalized = listingId.trim();

        if (!needsPublicationResolution(state, normalized)) {
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

    static boolean publicationResolutionCompleteFor(
            Long modelId,
            Collection<String> listingIds
    ) {
        State state = CURRENT.get();

        if (state == null
                || modelId == null
                || !modelId.equals(state.modelId())) {
            return false;
        }

        for (String listingId : normalizeIds(listingIds)) {
            if (needsPublicationResolution(state, listingId)
                    && !state.publishedAtByListingId().containsKey(listingId)) {
                return false;
            }
        }

        return true;
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

        Set<String> acceptedIds = normalizeIds(listingIds);
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

    private static boolean needsPublicationResolution(
            State state,
            String listingId
    ) {
        return state.refreshAllPublicationTimes()
                || !state.knownListingIds().contains(listingId)
                || state.missingPublicationListingIds().contains(listingId);
    }

    private static Set<String> normalizeIds(Collection<String> listingIds) {
        Set<String> normalized = new LinkedHashSet<>();

        if (listingIds == null) {
            return normalized;
        }

        for (String listingId : listingIds) {
            if (listingId != null && !listingId.isBlank()) {
                normalized.add(listingId.trim());
            }
        }

        return normalized;
    }

    private record State(
            Long modelId,
            Set<String> knownListingIds,
            Set<String> missingPublicationListingIds,
            boolean refreshAllPublicationTimes,
            Set<String> attemptedListingIds,
            Map<String, LocalDateTime> publishedAtByListingId
    ) {
    }
}
