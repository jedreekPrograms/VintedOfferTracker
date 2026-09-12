package pl.flipbot.playwright.marketstats;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.ApiClient;
import pl.flipbot.playwright.exception.ApiException;
import pl.flipbot.playwright.marketstats.dto.KnownMarketListingIdsDto;
import pl.flipbot.playwright.marketstats.dto.MarketListingPublicationBatchRequestDto;
import pl.flipbot.playwright.marketstats.dto.MarketObservationBatchRequestDto;
import pl.flipbot.playwright.marketstats.dto.MarketObservationBatchResponseDto;
import pl.flipbot.playwright.marketstats.dto.MarketStatsTargetDto;
import pl.flipbot.playwright.model.BotDetailsDto;

import java.net.http.HttpResponse;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class MarketStatsApiClient extends ApiClient {

    private static final String ANONYMOUS_OBSERVER_NAME =
            "Anonymous Market Observer";

    private final Map<Long, MarketStatsTargetDto> loadedTargets =
            new HashMap<>();

    public BotDetailsDto getObserverBot(
            Long ignoredObserverBotId
    ) {
        HttpResponse<String> response = get(
                "/api/market-stats/observer/playwright"
        );

        if (response.statusCode() == 204) {
            throw new ApiException(
                    "Market statistics observer is not configured yet."
            );
        }

        requireSuccess(response, "load market-stats observer bot");

        BotDetailsDto observer = readBody(
                response,
                BotDetailsDto.class
        );

        if (observer == null || observer.getId() == null) {
            throw new ApiException(
                    "Market statistics observer response did not contain a valid bot id."
            );
        }

        observer.setName(ANONYMOUS_OBSERVER_NAME);
        observer.setEmail(null);
        observer.setPassword(null);

        return observer;
    }

    public List<MarketStatsTargetDto> getTargets() {
        HttpResponse<String> response = get(
                "/api/market-stats/targets"
        );
        requireSuccess(response, "load market-stats targets");

        List<MarketStatsTargetDto> targets = readBody(
                response,
                new TypeReference<List<MarketStatsTargetDto>>() {
                }
        );

        loadedTargets.clear();

        for (MarketStatsTargetDto target : targets) {
            if (target != null && target.modelId() != null) {
                loadedTargets.put(target.modelId(), target);
            }
        }

        return targets;
    }

    public boolean isScanNeeded() {
        HttpResponse<String> response = get(
                "/api/market-stats/scan-needed"
        );
        requireSuccess(response, "check pending market-stat baseline work");

        Boolean result = readBody(response, Boolean.class);
        return Boolean.TRUE.equals(result);
    }

    public KnownMarketListingIdsDto getKnownListingIds(
            Long modelId
    ) {
        HttpResponse<String> response = get(
                "/api/market-stats/models/"
                        + modelId
                        + "/known-listing-ids"
        );
        requireSuccess(response, "load known market listing ids");

        KnownMarketListingIdsDto knownState = readBody(
                response,
                KnownMarketListingIdsDto.class
        );

        HttpResponse<String> missingPublicationResponse = get(
                "/api/market-stats/models/"
                        + modelId
                        + "/missing-publication-listing-ids"
        );
        requireSuccess(
                missingPublicationResponse,
                "load listings missing Vinted publication time"
        );

        List<String> missingPublicationListingIds = readBody(
                missingPublicationResponse,
                new TypeReference<List<String>>() {
                }
        );

        Map<String, LocalDateTime> persistedPublicationTimes =
                loadPersistedPublicationTimes(modelId);

        HttpResponse<String> fullScanResponse = get(
                "/api/market-stats/models/"
                        + modelId
                        + "/full-catalog-scan-required"
        );
        requireSuccess(
                fullScanResponse,
                "check whether a full market catalog retry is required"
        );

        boolean fullCatalogScanRequired = Boolean.TRUE.equals(
                readBody(fullScanResponse, Boolean.class)
        );

        /*
         * A forced traversal must not throw away exact publication timestamps
         * that were already persisted by a previous attempt. They are immutable
         * Vinted facts and are now seeded into the observation context so both
         * publication-completeness checks and the historical stop boundary can
         * use them without reopening those item pages.
         */
        MarketStatsObservationContext.begin(
                modelId,
                knownState.listingIds(),
                missingPublicationListingIds,
                false,
                fullCatalogScanRequired,
                persistedPublicationTimes
        );

        if (!fullCatalogScanRequired) {
            return knownState;
        }

        log.info(
                "[MARKET STATS] Model {} requires a full filtered-catalog traversal because its baseline/publication window is unfinished or its previous scan was incomplete. Already stored publication timestamps are reused; only new/missing timestamps are resolved again.",
                modelId
        );

        /*
         * MarketStatsCollector uses known listing ids only as an early-stop
         * boundary. Keep the real ids and their publication timestamps in the
         * observation context above, but hide that boundary for this retry so
         * the collector must prove a complete filtered-catalog/statistics window.
         */
        return new KnownMarketListingIdsDto(
                knownState.modelId(),
                List.of(),
                knownState.baselineComplete()
        );
    }

    public MarketObservationBatchResponseDto recordObservations(
            Long modelId,
            List<String> listingIds,
            boolean complete
    ) {
        List<String> acceptedIds = listingIds == null
                ? List.of()
                : listingIds;

        try {
            boolean publicationComplete =
                    MarketStatsObservationContext
                            .publicationResolutionCompleteFor(
                                    modelId,
                                    acceptedIds
                            );
            boolean effectiveComplete = complete && publicationComplete;
            boolean forcedFullCatalogScan =
                    MarketStatsObservationContext.fullCatalogScanRequired(modelId);

            if (complete && !publicationComplete) {
                log.warn(
                        "[MARKET STATS] Model {} catalog scan reached its normal completion boundary, "
                                + "but at least one accepted listing still has no Vinted publication time. "
                                + "The scan will remain incomplete and be retried.",
                        modelId
                );
            }

            MarketObservationBatchResponseDto recorded;

            if (acceptedIds.isEmpty()) {
                recorded = postObservations(
                        modelId,
                        acceptedIds,
                        effectiveComplete
                );
            } else {
                /*
                 * First persist the observed IDs with lastScanComplete=false.
                 * New rows must exist before the publication-time update can
                 * target them. Only after fresh publication timestamps have been
                 * stored do we mark a fully resolved pass complete.
                 */
                recorded = postObservations(
                        modelId,
                        acceptedIds,
                        false
                );

                flushResolvedPublicationTimes(
                        modelId,
                        acceptedIds
                );

                if (effectiveComplete) {
                    recorded = postObservations(
                            modelId,
                            acceptedIds,
                            true
                    );
                }
            }

            if (effectiveComplete && forcedFullCatalogScan) {
                markPublicationWindowComplete(modelId);
            }

            return recorded;
        } finally {
            MarketStatsObservationContext.clear(modelId);
        }
    }

    public void clearObservationContext(Long modelId) {
        try {
            List<String> observedIds = MarketStatsObservationContext
                    .observedListingIds(modelId)
                    .stream()
                    .toList();

            if (!observedIds.isEmpty()) {
                postObservations(modelId, observedIds, false);
                flushResolvedPublicationTimes(modelId, observedIds);

                log.info(
                        "[MARKET STATS] Preserved partial progress for model {} after an interrupted scan. observedIds={}.",
                        modelId,
                        observedIds.size()
                );
            }
        } catch (RuntimeException persistenceFailure) {
            log.warn(
                    "[MARKET STATS] Could not persist partial progress for model {} before clearing the interrupted observation context.",
                    modelId,
                    persistenceFailure
            );
        } finally {
            MarketStatsObservationContext.clear(modelId);
        }
    }

    private MarketObservationBatchResponseDto postObservations(
            Long modelId,
            List<String> listingIds,
            boolean complete
    ) {
        MarketStatsTargetDto target = loadedTargets.get(modelId);

        HttpResponse<String> response = post(
                "/api/market-stats/models/"
                        + modelId
                        + "/observations",
                new MarketObservationBatchRequestDto(
                        listingIds,
                        complete,
                        target == null ? null : target.minPrice(),
                        target == null ? null : target.maxPrice()
                )
        );

        requireSuccess(response, "record market listing observations");

        return readBody(
                response,
                MarketObservationBatchResponseDto.class
        );
    }

    private Map<String, LocalDateTime> loadPersistedPublicationTimes(
            Long modelId
    ) {
        HttpResponse<String> response = get(
                "/api/market-stats/models/"
                        + modelId
                        + "/publication-times"
        );
        requireSuccess(
                response,
                "load stored Vinted publication times"
        );

        Map<String, String> raw = readBody(
                response,
                new TypeReference<Map<String, String>>() {
                }
        );

        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }

        Map<String, LocalDateTime> parsed = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : raw.entrySet()) {
            String listingId = entry.getKey();
            String value = entry.getValue();

            if (listingId == null
                    || listingId.isBlank()
                    || value == null
                    || value.isBlank()) {
                continue;
            }

            try {
                parsed.put(
                        listingId.trim(),
                        LocalDateTime.parse(value.trim())
                );
            } catch (DateTimeException exception) {
                log.warn(
                        "[MARKET STATS] Ignoring malformed stored publication time for model {} / listing {}: '{}'. The listing will be resolved again if encountered.",
                        modelId,
                        listingId,
                        value
                );
            }
        }

        return Map.copyOf(parsed);
    }

    private void flushResolvedPublicationTimes(
            Long modelId,
            List<String> listingIds
    ) {
        Map<String, LocalDateTime> resolved =
                MarketStatsObservationContext.freshlyResolvedFor(
                        modelId,
                        listingIds
                );

        if (resolved.isEmpty()) {
            return;
        }

        Map<String, String> payload = new LinkedHashMap<>();

        for (Map.Entry<String, LocalDateTime> entry : resolved.entrySet()) {
            payload.put(
                    entry.getKey(),
                    entry.getValue().toString()
            );
        }

        HttpResponse<String> response = post(
                "/api/market-stats/models/"
                        + modelId
                        + "/publication-times",
                new MarketListingPublicationBatchRequestDto(
                        Map.copyOf(payload)
                )
        );

        requireSuccess(response, "record market listing publication times");

        Integer updated = readBody(response, Integer.class);

        if (updated == null || updated < payload.size()) {
            throw new ApiException(
                    "Backend stored Vinted publication time for only "
                            + updated
                            + " of "
                            + payload.size()
                            + " freshly resolved listings in model "
                            + modelId
                            + "."
            );
        }
    }

    private void markPublicationWindowComplete(Long modelId) {
        HttpResponse<String> response = post(
                "/api/market-stats/models/"
                        + modelId
                        + "/publication-window-complete"
        );

        requireSuccess(
                response,
                "mark market publication window complete"
        );

        log.info(
                "[MARKET STATS] Model {} established a complete Vinted publication-time window. Future scans may safely reuse persisted timestamps and the known-listing boundary.",
                modelId
        );
    }

    private void requireSuccess(
            HttpResponse<String> response,
            String operation
    ) {
        int statusCode = response.statusCode();

        if (statusCode >= 200 && statusCode < 300) {
            return;
        }

        throw new ApiException(
                "Could not "
                        + operation
                        + ". HTTP status="
                        + statusCode
                        + ", response="
                        + response.body()
        );
    }
}
