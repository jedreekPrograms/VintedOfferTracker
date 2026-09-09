package pl.flipbot.marketstats;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.flipbot.dictionary.DictionaryModel;
import pl.flipbot.dictionary.DictionaryModelRepository;
import pl.flipbot.marketstats.dto.MarketObservationBatchRequest;
import pl.flipbot.marketstats.dto.MarketObservationBatchResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class MarketStatsObservationService {

    private static final int OBSERVATION_RETENTION_DAYS = 30;
    private static final ZoneId MARKET_STATS_ZONE = ZoneId.of("Europe/Warsaw");

    private final DictionaryModelRepository modelRepository;
    private final MarketModelScanStateRepository scanStateRepository;
    private final MarketListingObservationRepository observationRepository;

    @Transactional
    public MarketObservationBatchResponse recordObservations(
            Long modelId,
            MarketObservationBatchRequest request
    ) {
        Objects.requireNonNull(
                request,
                "Market observation request cannot be null"
        );

        DictionaryModel model = modelRepository.findByIdForUpdate(modelId)
                .orElseThrow(
                        () -> new NoSuchElementException(
                                "Dictionary model was not found: " + modelId
                        )
                );

        if (!samePrice(model.getMarketMinPrice(), request.minPrice())
                || !samePrice(model.getMarketMaxPrice(), request.maxPrice())) {
            throw new IllegalStateException(
                    "Market observer price range changed while model "
                            + modelId
                            + " was being scanned. The stale observation batch was rejected."
            );
        }

        List<String> listingIds = normalizeListingIds(request.listingIds());

        if (listingIds.isEmpty() && !request.complete()) {
            throw new IllegalArgumentException(
                    "An incomplete market scan must contain at least one marketplace listing id."
            );
        }

        LocalDateTime now = LocalDateTime.now(MARKET_STATS_ZONE);
        Map<String, LocalDateTime> publishedAtByListingId =
                parsePublishedAtByListingId(
                        listingIds,
                        request.publishedAtByListingId(),
                        now
                );

        MarketModelScanState state = scanStateRepository
                .findByModelIdForUpdate(modelId)
                .orElse(null);

        boolean createdState = state == null;

        if (state == null) {
            state = MarketModelScanState.builder()
                    .model(model)
                    .initializedAt(now)
                    .baselineCompleteAt(null)
                    .baselineOfferCount(null)
                    .lastScanAt(now)
                    .lastSuccessfulScanAt(null)
                    .lastScanComplete(false)
                    .build();

            state = scanStateRepository.saveAndFlush(state);
        }

        boolean baselineMode = state.getBaselineCompleteAt() == null;

        Map<String, MarketListingObservation> existingById = new HashMap<>();

        if (!listingIds.isEmpty()) {
            observationRepository
                    .findAllByModel_IdAndMarketplaceListingIdIn(
                            modelId,
                            listingIds
                    )
                    .forEach(observation -> existingById.put(
                            observation.getMarketplaceListingId(),
                            observation
                    ));
        }

        List<MarketListingObservation> changed = new ArrayList<>();
        int newListings = 0;

        for (String listingId : listingIds) {
            LocalDateTime publishedAt = publishedAtByListingId.get(listingId);
            MarketListingObservation existing = existingById.get(listingId);

            if (existing != null) {
                /*
                 * On this isolated observer branch first_seen_at intentionally
                 * stores the Vinted publication moment. Keep the first resolved
                 * value stable: relative labels such as "15 godzin temu" are
                 * rounded by Vinted and would otherwise drift on every pass.
                 */
                existing.setLastSeenAt(now);
                changed.add(existing);
                continue;
            }

            boolean baseline = baselineMode;

            changed.add(
                    MarketListingObservation.builder()
                            .model(model)
                            .marketplaceListingId(listingId)
                            .firstSeenAt(publishedAt)
                            .lastSeenAt(now)
                            .baseline(baseline)
                            .build()
            );

            if (!baseline) {
                newListings++;
            }
        }

        if (!changed.isEmpty()) {
            observationRepository.saveAll(changed);
        }

        state.setLastScanAt(now);
        state.setLastScanComplete(request.complete());

        if (request.complete()) {
            state.setLastSuccessfulScanAt(now);

            if (state.getBaselineCompleteAt() == null) {
                observationRepository.flush();
                state.setBaselineCompleteAt(now);
                state.setBaselineOfferCount(
                        safeInt(
                                observationRepository.countByModel_IdAndBaselineTrue(
                                        modelId
                                )
                        )
                );
            }
        }

        scanStateRepository.save(state);

        observationRepository.deleteByLastSeenAtBefore(
                now.minusDays(OBSERVATION_RETENTION_DAYS)
        );

        return new MarketObservationBatchResponse(
                modelId,
                createdState || baselineMode,
                listingIds.size(),
                newListings,
                now,
                request.complete()
        );
    }

    private Map<String, LocalDateTime> parsePublishedAtByListingId(
            List<String> listingIds,
            Map<String, String> rawValues,
            LocalDateTime now
    ) {
        if (listingIds.isEmpty()) {
            return Map.of();
        }

        if (rawValues == null || rawValues.isEmpty()) {
            throw new IllegalArgumentException(
                    "Every market listing observation must include the Vinted publication time."
            );
        }

        Map<String, LocalDateTime> result = new HashMap<>();

        for (String listingId : listingIds) {
            String raw = rawValues.get(listingId);

            if (raw == null || raw.isBlank()) {
                throw new IllegalArgumentException(
                        "Missing Vinted publication time for listing " + listingId
                );
            }

            LocalDateTime publishedAt;

            try {
                publishedAt = LocalDateTime.parse(raw.trim());
            } catch (DateTimeParseException exception) {
                throw new IllegalArgumentException(
                        "Invalid Vinted publication time for listing "
                                + listingId
                                + ": "
                                + raw,
                        exception
                );
            }

            if (publishedAt.isAfter(now.plusMinutes(5))) {
                throw new IllegalArgumentException(
                        "Vinted publication time is in the future for listing "
                                + listingId
                                + ": "
                                + publishedAt
                );
            }

            result.put(listingId, publishedAt);
        }

        return result;
    }

    private List<String> normalizeListingIds(List<String> listingIds) {
        if (listingIds == null || listingIds.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> normalized = new LinkedHashSet<>();

        for (String listingId : listingIds) {
            if (listingId == null) {
                continue;
            }

            String trimmed = listingId.trim();

            if (!trimmed.isBlank()) {
                normalized.add(trimmed);
            }
        }

        return List.copyOf(normalized);
    }

    private boolean samePrice(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return left == right;
        }

        return left.compareTo(right) == 0;
    }

    private int safeInt(long value) {
        return value > Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : (int) Math.max(value, 0L);
    }
}
