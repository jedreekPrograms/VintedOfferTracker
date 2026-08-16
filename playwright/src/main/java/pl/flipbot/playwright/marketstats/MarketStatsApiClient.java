package pl.flipbot.playwright.marketstats;

import com.fasterxml.jackson.core.type.TypeReference;
import pl.flipbot.playwright.api.ApiClient;
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
        return readBody(response, BotDetailsDto.class);
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
