package pl.flipbot.playwright.marketstats;

import com.fasterxml.jackson.core.type.TypeReference;
import pl.flipbot.playwright.api.ApiClient;
import pl.flipbot.playwright.exception.ApiException;
import pl.flipbot.playwright.marketstats.dto.*;
import pl.flipbot.playwright.model.BotDetailsDto;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Set;

public class MarketStatsApiClient extends ApiClient {

    public BotDetailsDto getObserverBot(
            Long botId
    ) {
        HttpResponse<String> response = get(
                "/api/market-stats/observer-bots/" + botId
        );
        requireSuccess(response, "load market-stats observer bot");
        return readBody(response, BotDetailsDto.class);
    }

    public List<MarketStatsTargetDto> getTargets() {
        HttpResponse<String> response = get(
                "/api/market-stats/targets"
        );
        requireSuccess(response, "load market-stats targets");
        return readBody(
                response,
                new TypeReference<List<MarketStatsTargetDto>>() {
                }
        );
    }

    public Set<String> getKnownListingIds(
            Long modelId
    ) {
        HttpResponse<String> response = get(
                "/api/market-stats/models/"
                        + modelId
                        + "/known-listing-ids"
        );
        requireSuccess(response, "load known market listing ids");

        KnownMarketListingIdsDto body = readBody(
                response,
                KnownMarketListingIdsDto.class
        );

        return body.listingIds() == null
                ? Set.of()
                : Set.copyOf(body.listingIds());
    }

    public MarketObservationBatchResponseDto recordObservations(
            Long modelId,
            List<String> listingIds,
            boolean complete
    ) {
        HttpResponse<String> response = post(
                "/api/market-stats/models/"
                        + modelId
                        + "/observations",
                new MarketObservationBatchRequestDto(
                        listingIds,
                        complete
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
