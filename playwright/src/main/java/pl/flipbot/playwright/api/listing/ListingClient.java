package pl.flipbot.playwright.api.listing;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.ApiClient;
import pl.flipbot.playwright.api.listing.dto.DiscoverListingsRequestDto;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.api.listing.dto.NegotiationCapacityResponseDto;
import pl.flipbot.playwright.api.listing.dto.UpdateConversationIdentityRequestDto;
import pl.flipbot.playwright.api.listing.dto.UpdateListingRequestDto;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Objects;

@Slf4j
public class ListingClient extends ApiClient {

    public List<ListingResponseDto> discoverListings(
            Long botId,
            DiscoverListingsRequestDto request
    ) {
        return discoverListings(botId, null, request);
    }

    public List<ListingResponseDto> discoverListings(
            Long botId,
            Long additionalTargetId,
            DiscoverListingsRequestDto request
    ) {

        Objects.requireNonNull(botId, "Bot id cannot be null");
        Objects.requireNonNull(request, "Discover listings request cannot be null");

        int listingCount = request.listings() == null
                ? 0
                : request.listings().size();

        log.info(
                "Sending {} discovered listings to backend for bot {}, target {}",
                listingCount,
                botId,
                additionalTargetId == null ? "MAIN" : additionalTargetId
        );

        String path = additionalTargetId == null
                ? "/api/bots/" + botId + "/listings/discover"
                : "/api/bots/" + botId + "/listings/discover/additional/" + additionalTargetId;

        HttpResponse<String> response = post(path, request);
        validateResponse(
                response,
                "discover listings for bot " + botId
                        + " target " + (additionalTargetId == null ? "MAIN" : additionalTargetId)
        );

        if (isEmptyBody(response)) {
            log.info("Backend returned no new listings for bot {}", botId);
            return List.of();
        }

        List<ListingResponseDto> claimedListings = readListingList(response);
        log.info(
                "Backend assigned {} new listings to bot {} target {}",
                claimedListings.size(),
                botId,
                additionalTargetId == null ? "MAIN" : additionalTargetId
        );
        return claimedListings;
    }

    /**
     * Legacy all-target view retained for non-catalog callers. New catalog
     * processing should use getDiscoveredListings(botId, targetId), where NULL
     * explicitly means the original/main product.
     */
    public List<ListingResponseDto> getDiscoveredListings(Long botId) {
        Objects.requireNonNull(botId, "Bot id cannot be null");
        return loadDiscovered(
                botId,
                "/api/bots/" + botId + "/listings/discovered",
                "ALL"
        );
    }

    public List<ListingResponseDto> getDiscoveredListings(
            Long botId,
            Long additionalTargetId
    ) {
        Objects.requireNonNull(botId, "Bot id cannot be null");

        String path = additionalTargetId == null
                ? "/api/bots/" + botId + "/listings/discovered/primary"
                : "/api/bots/" + botId + "/listings/discovered/additional/" + additionalTargetId;

        return loadDiscovered(
                botId,
                path,
                additionalTargetId == null ? "MAIN" : additionalTargetId.toString()
        );
    }

    private List<ListingResponseDto> loadDiscovered(
            Long botId,
            String path,
            String targetLabel
    ) {
        HttpResponse<String> response = get(path);
        validateResponse(
                response,
                "load discovered listings for bot " + botId + " target " + targetLabel
        );

        if (isEmptyBody(response)) {
            return List.of();
        }

        List<ListingResponseDto> listings = readListingList(response);
        log.info(
                "Loaded {} discovered listings for bot {} target {}",
                listings.size(),
                botId,
                targetLabel
        );
        return listings;
    }

    public List<ListingResponseDto> getNegotiatingListings(
            Long botId
    ) {

        Objects.requireNonNull(
                botId,
                "Bot id cannot be null"
        );

        String path =
                "/api/bots/"
                        + botId
                        + "/listings/negotiating";

        HttpResponse<String> response =
                get(
                        path
                );

        validateResponse(
                response,
                "load negotiating listings for bot "
                        + botId
        );

        if (isEmptyBody(response)) {
            return List.of();
        }

        List<ListingResponseDto> listings =
                readListingList(
                        response
                );

        log.info(
                "Loaded {} negotiating listings for bot {}",
                listings.size(),
                botId
        );

        return listings;

    }

    public int getAllowedNewNegotiations(
            Long botId
    ) {

        Objects.requireNonNull(
                botId,
                "Bot id cannot be null"
        );

        String path =
                "/api/bots/"
                        + botId
                        + "/listings/negotiation-capacity";

        HttpResponse<String> response =
                get(
                        path
                );

        validateResponse(
                response,
                "calculate negotiation capacity for bot "
                        + botId
        );

        NegotiationCapacityResponseDto capacity =
                readBody(
                        response,
                        NegotiationCapacityResponseDto.class
                );

        int allowedNewNegotiations =
                Math.max(
                        capacity.allowedNewNegotiations(),
                        0
                );

        log.info(
                "Bot {} may start {} new negotiations",
                botId,
                allowedNewNegotiations
        );

        return allowedNewNegotiations;

    }

    public ListingResponseDto updateConversationIdentity(
            Long botId,
            Long backendListingId,
            UpdateConversationIdentityRequestDto request
    ) {
        Objects.requireNonNull(botId, "Bot id cannot be null");
        Objects.requireNonNull(
                backendListingId,
                "Backend listing id cannot be null"
        );
        Objects.requireNonNull(
                request,
                "Conversation identity request cannot be null"
        );

        String path =
                "/api/bots/"
                        + botId
                        + "/listings/"
                        + backendListingId
                        + "/conversation";

        HttpResponse<String> response =
                patch(
                        path,
                        request
                );

        validateResponse(
                response,
                "update conversation identity for listing "
                        + backendListingId
                        + " / bot "
                        + botId
        );

        ListingResponseDto updated =
                readBody(
                        response,
                        ListingResponseDto.class
                );

        log.warn(
                "[CONVERSATION] Backend canonical conversation identity updated for listing {}. conversationId={}, conversationUrl={}",
                backendListingId,
                updated.conversationId(),
                updated.conversationUrl()
        );

        return updated;
    }

    public ListingResponseDto updateListing(
            Long botId,
            Long backendListingId,
            UpdateListingRequestDto request
    ) {

        Objects.requireNonNull(
                botId,
                "Bot id cannot be null"
        );

        Objects.requireNonNull(
                backendListingId,
                "Backend listing id cannot be null"
        );

        Objects.requireNonNull(
                request,
                "Update listing request cannot be null"
        );

        String path =
                "/api/bots/"
                        + botId
                        + "/listings/"
                        + backendListingId;

        HttpResponse<String> response =
                patch(
                        path,
                        request
                );

        validateResponse(
                response,
                "update listing "
                        + backendListingId
                        + " for bot "
                        + botId
        );

        ListingResponseDto updatedListing =
                readBody(
                        response,
                        ListingResponseDto.class
                );

        log.info(
                "Updated backend listing {}. Status: {}, step: {}, price: {}",
                updatedListing.id(),
                updatedListing.status(),
                updatedListing.currentStep(),
                updatedListing.currentPrice()
        );

        return updatedListing;

    }

    private List<ListingResponseDto> readListingList(
            HttpResponse<String> response
    ) {

        return readBody(
                response,
                new TypeReference<
                        List<ListingResponseDto>
                        >() {
                }
        );

    }

    private boolean isEmptyBody(
            HttpResponse<String> response
    ) {

        return response.body() == null
                || response.body().isBlank();

    }

    private void validateResponse(
            HttpResponse<String> response,
            String operation
    ) {

        int statusCode =
                response.statusCode();

        if (statusCode >= 200
                && statusCode < 300) {

            return;

        }

        throw new IllegalStateException(
                "Backend could not "
                        + operation
                        + ". HTTP status: "
                        + statusCode
                        + ", response: "
                        + response.body()
        );

    }

}
