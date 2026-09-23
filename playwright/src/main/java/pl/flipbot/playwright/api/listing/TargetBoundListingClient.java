package pl.flipbot.playwright.api.listing;

import pl.flipbot.playwright.api.listing.dto.DiscoverListingsRequestDto;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.api.listing.dto.NegotiationCapacityResponseDto;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Objects;

/**
 * Product-scoped client. NULL targetId means the original product; otherwise
 * discovery/backlog/capacity and active-negotiation reads are isolated to one
 * additional product.
 */
public class TargetBoundListingClient extends ListingClient {

    private final Long additionalTargetId;

    public TargetBoundListingClient(Long additionalTargetId) {
        this.additionalTargetId = additionalTargetId;
    }

    @Override
    public List<ListingResponseDto> discoverListings(
            Long botId,
            DiscoverListingsRequestDto request
    ) {
        return super.discoverListings(botId, additionalTargetId, request);
    }

    @Override
    public List<ListingResponseDto> getDiscoveredListings(Long botId) {
        return super.getDiscoveredListings(botId, additionalTargetId);
    }

    @Override
    public List<ListingResponseDto> getNegotiatingListings(Long botId) {
        return super.getNegotiatingListings(botId)
                .stream()
                .filter(listing -> Objects.equals(
                        listing.additionalTargetId(),
                        additionalTargetId
                ))
                .toList();
    }

    @Override
    public int getAllowedNewNegotiations(Long botId) {
        if (additionalTargetId == null) {
            return super.getAllowedNewNegotiations(botId);
        }

        String path = "/api/bots/"
                + botId
                + "/listings/negotiation-capacity/additional/"
                + additionalTargetId;

        HttpResponse<String> response = get(path);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(
                    "Backend could not calculate negotiation capacity for bot "
                            + botId
                            + " additional target "
                            + additionalTargetId
                            + ". HTTP status: "
                            + response.statusCode()
                            + ", response: "
                            + response.body()
            );
        }

        NegotiationCapacityResponseDto capacity = readBody(
                response,
                NegotiationCapacityResponseDto.class
        );
        return Math.max(capacity.allowedNewNegotiations(), 0);
    }
}
