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

        MarketStatsObservationContext.begin(
                modelId,
                knownState.listingIds(),
                missingPublicationListingIds,
                fullCatalogScanRequired
        );

        if (!fullCatalogScanRequired) {
            return knownState;
        }

        log.info(
                "[MARKET STATS] Model {} previous scan was incomplete (or baseline is unfinished). "
                        + "Forcing a full filtered-catalog traversal and refreshing publication times for every visible listing before the known-listing boundary can be trusted again.",
                modelId
        );

        /*
         * MarketStatsCollector uses known listing ids only as an early-stop
         * boundary. Keep the real ids in MarketStatsObservationContext above,
         * but hide that boundary for this retry so the collector must walk the
         * complete filtered catalog. Once the backend records a complete pass,
         * later scans receive the normal known-id list again.
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

            if (complete && !publicationComplete) {
                log.warn(
                        "[MARKET STATS] Model {} catalog scan reached its normal completion boundary, "
                                + "but at least one required Vinted publication time is still unresolved. "
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
                 * target them. Only after the publication timestamps have been
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

            return recorded;
        } finally {
            MarketStatsObservationContext.clear(modelId);
        }
    }

    public void clearObservationContext(Long modelId) {
        MarketStatsObservationContext.clear(modelId);
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

    private void flushResolvedPublicationTimes(
            Long modelId,
            List<String> listingIds
    ) {
        Map<String, LocalDateTime> resolved =
                MarketStatsObservationContext.resolvedFor(
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
                            + " resolved listings in model "
                            + modelId
                            + "."
            );
        }
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
