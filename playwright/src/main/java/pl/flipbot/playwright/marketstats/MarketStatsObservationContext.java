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
            boolean refreshAllPublicationTimes,
            boolean fullCatalogScanRequired,
            Map<String, LocalDateTime> persistedPublishedAtByListingId
    ) {
        CURRENT.set(
                new State(
                        modelId,
                        Set.copyOf(normalizeIds(knownListingIds)),
                        Set.copyOf(normalizeIds(missingPublicationListingIds)),
                        refreshAllPublicationTimes,
                        fullCatalogScanRequired,
                        new LinkedHashSet<>(),
                        new LinkedHashSet<>(),
                        new LinkedHashMap<>(normalizePublishedAt(
                                persistedPublishedAtByListingId
                        )),
                        new LinkedHashMap<>()
                )
        );
    }

    static void recordObservedListingId(String listingId) {
        State state = CURRENT.get();

        if (state == null || listingId == null || listingId.isBlank()) {
            return;
        }

        state.observedListingIds().add(listingId.trim());
    }

    static Set<String> observedListingIds(Long modelId) {
        State state = CURRENT.get();

        if (state == null
                || modelId == null
                || !modelId.equals(state.modelId())) {
            return Set.of();
        }

        return Set.copyOf(state.observedListingIds());
    }

    static boolean fullCatalogScanRequired(Long modelId) {
        State state = CURRENT.get();

        return state != null
                && modelId != null
                && modelId.equals(state.modelId())
                && state.fullCatalogScanRequired();
    }

    static boolean needsPublicationResolution(String listingId) {
        State state = CURRENT.get();

        if (state == null || listingId == null || listingId.isBlank()) {
            return false;
        }

        String normalized = listingId.trim();

        return needsPublicationResolution(state, normalized);
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

        state.resolvedPublishedAtByListingId().put(
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
            if (publicationTime(state, listingId) == null) {
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

        for (String listingId : acceptedIds) {
            LocalDateTime publishedAt = publicationTime(state, listingId);
            if (publishedAt != null) {
                result.put(listingId, publishedAt);
            }
        }

        return Map.copyOf(result);
    }

    static Map<String, LocalDateTime> freshlyResolvedFor(
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
                : state.resolvedPublishedAtByListingId().entrySet()) {
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
                || state.missingPublicationListingIds().contains(listingId)
                || publicationTime(state, listingId) == null;
    }

    private static LocalDateTime publicationTime(
            State state,
            String listingId
    ) {
        LocalDateTime freshlyResolved =
                state.resolvedPublishedAtByListingId().get(listingId);

        if (freshlyResolved != null) {
            return freshlyResolved;
        }

        return state.persistedPublishedAtByListingId().get(listingId);
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

    private static Map<String, LocalDateTime> normalizePublishedAt(
            Map<String, LocalDateTime> publishedAtByListingId
    ) {
        Map<String, LocalDateTime> normalized = new LinkedHashMap<>();

        if (publishedAtByListingId == null) {
            return normalized;
        }

        for (Map.Entry<String, LocalDateTime> entry
                : publishedAtByListingId.entrySet()) {
            if (entry.getKey() == null
                    || entry.getKey().isBlank()
                    || entry.getValue() == null) {
                continue;
            }

            normalized.put(entry.getKey().trim(), entry.getValue());
        }

        return normalized;
    }

    private record State(
            Long modelId,
            Set<String> knownListingIds,
            Set<String> missingPublicationListingIds,
            boolean refreshAllPublicationTimes,
            boolean fullCatalogScanRequired,
            Set<String> observedListingIds,
            Set<String> attemptedListingIds,
            Map<String, LocalDateTime> persistedPublishedAtByListingId,
            Map<String, LocalDateTime> resolvedPublishedAtByListingId
    ) {
    }
}
