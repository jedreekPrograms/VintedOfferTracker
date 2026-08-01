package pl.flipbot.playwright.api.listing;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.ApiClient;
import pl.flipbot.playwright.api.listing.dto.DiscoverListingsRequestDto;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Objects;

@Slf4j
public class ListingClient extends ApiClient {

    public List<ListingResponseDto> discoverListings(
            Long botId,
            DiscoverListingsRequestDto request
    ) {

        Objects.requireNonNull(
                botId,
                "Bot id cannot be null"
        );

        Objects.requireNonNull(
                request,
                "Discover listings request cannot be null"
        );

        int listingCount =
                request.listings() == null
                        ? 0
                        : request.listings().size();

        log.info(
                "Sending {} discovered listings to backend for bot {}",
                listingCount,
                botId
        );

        String path =
                "/api/bots/"
                        + botId
                        + "/listings/discover";

        HttpResponse<String> response =
                post(
                        path,
                        request
                );

        validateResponse(
                response,
                botId
        );

        if (response.body() == null
                || response.body().isBlank()) {

            log.info(
                    "Backend returned no new listings for bot {}",
                    botId
            );

            return List.of();

        }

        List<ListingResponseDto> claimedListings =
                readBody(
                        response,
                        new TypeReference<
                                List<ListingResponseDto>
                                >() {
                        }
                );

        log.info(
                "Backend assigned {} new listings to bot {}",
                claimedListings.size(),
                botId
        );

        return claimedListings;

    }

    private void validateResponse(
            HttpResponse<String> response,
            Long botId
    ) {

        int statusCode =
                response.statusCode();

        if (statusCode >= 200
                && statusCode < 300) {

            return;

        }

        throw new IllegalStateException(
                "Backend rejected listing discovery request for bot "
                        + botId
                        + ". HTTP status: "
                        + statusCode
                        + ", response: "
                        + response.body()
        );

    }

}