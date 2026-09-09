package pl.flipbot.playwright.marketstats;

import com.fasterxml.jackson.core.type.TypeReference;
import pl.flipbot.playwright.api.ApiClient;
import pl.flipbot.playwright.browser.BrowserManager;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.exception.ApiException;
import pl.flipbot.playwright.marketstats.dto.KnownMarketListingIdsDto;
import pl.flipbot.playwright.marketstats.dto.MarketObservationBatchRequestDto;
import pl.flipbot.playwright.marketstats.dto.MarketObservationBatchResponseDto;
import pl.flipbot.playwright.marketstats.dto.MarketStatsTargetDto;
import pl.flipbot.playwright.model.BotDetailsDto;

import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MarketStatsApiClient extends ApiClient {

    private final Map<Long, MarketStatsTargetDto> loadedTargets =
            new HashMap<>();

    private BotDetailsDto loadedObserverBot;

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
        loadedObserverBot = readBody(response, BotDetailsDto.class);
        return loadedObserverBot;
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

        return readBody(
                response,
                KnownMarketListingIdsDto.class
        );
    }

    public MarketObservationBatchResponseDto recordObservations(
            Long modelId,
            List<String> listingIds,
            boolean complete
    ) {
        List<String> requestedIds = listingIds == null
                ? List.of()
                : listingIds;

        Map<String, String> publishedAtByListingId =
                resolvePublicationTimes(requestedIds);

        if (!requestedIds.isEmpty()
                && publishedAtByListingId.size() != requestedIds.size()) {
            throw new ApiException(
                    "Resolved Vinted publication time for "
                            + publishedAtByListingId.size()
                            + " of "
                            + requestedIds.size()
                            + " listings in model "
                            + modelId
                            + ". Refusing to record a partial or guessed baseline."
            );
        }

        return recordObservations(
                modelId,
                requestedIds,
                publishedAtByListingId,
                complete
        );
    }

    public MarketObservationBatchResponseDto recordObservations(
            Long modelId,
            List<String> listingIds,
            Map<String, String> publishedAtByListingId,
            boolean complete
    ) {
        MarketStatsTargetDto target = loadedTargets.get(modelId);

        HttpResponse<String> response = post(
                "/api/market-stats/models/"
                        + modelId
                        + "/observations",
                new MarketObservationBatchRequestDto(
                        listingIds,
                        publishedAtByListingId,
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

    private Map<String, String> resolvePublicationTimes(
            List<String> listingIds
    ) {
        if (listingIds == null || listingIds.isEmpty()) {
            return Map.of();
        }

        if (loadedObserverBot == null) {
            throw new ApiException(
                    "Market observer bot was not loaded before publication-time resolution."
            );
        }

        try (BrowserManager browserManager = new BrowserManager(true);
             BotContext detailContext = new BotContext(
                     loadedObserverBot,
                     browserManager
             )) {
            return new MarketListingPublishedAtResolver().resolve(
                    detailContext,
                    listingIds
            );
        } catch (RuntimeException exception) {
            throw new ApiException(
                    "Could not inspect Vinted listing publication times.",
                    exception
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
