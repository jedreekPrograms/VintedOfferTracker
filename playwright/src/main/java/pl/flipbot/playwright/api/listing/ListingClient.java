package pl.flipbot.playwright.api.listing;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.ApiClient;
import pl.flipbot.playwright.api.listing.dto.DiscoverListingsRequestDto;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.api.listing.dto.NegotiationCapacityResponseDto;
import pl.flipbot.playwright.api.listing.dto.UpdateListingRequestDto;
import pl.flipbot.playwright.marketplace.MarketplaceUrls;

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
                "discover listings for bot "
                        + botId
        );

        if (isEmptyBody(response)) {

            log.info(
                    "Backend returned no new listings for bot {}",
                    botId
            );

            return List.of();

        }

        List<ListingResponseDto> claimedListings =
                readListingList(
                        response
                );

        validateTrustedListings(
                claimedListings,
                "discover listings response for bot " + botId
        );

        log.info(
                "Backend assigned {} new listings to bot {}",
                claimedListings.size(),
                botId
        );

        return claimedListings;

    }

    public List<ListingResponseDto> getDiscoveredListings(
            Long botId
    ) {

        Objects.requireNonNull(
                botId,
                "Bot id cannot be null"
        );

        String path =
                "/api/bots/"
                        + botId
                        + "/listings/discovered";

        HttpResponse<String> response =
                get(
                        path
                );

        validateResponse(
                response,
                "load discovered listings for bot "
                        + botId
        );

        if (isEmptyBody(response)) {
            return List.of();
        }

        List<ListingResponseDto> listings =
                readListingList(
                        response
                );

        validateTrustedListings(
                listings,
                "discovered listings response for bot " + botId
        );

        log.info(
                "Loaded {} discovered listings for bot {}",
                listings.size(),
                botId
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

        validateTrustedListings(
                listings,
                "negotiating listings response for bot " + botId
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

        validateTrustedListing(
                updatedListing,
                "update listing response for backend listing "
                        + backendListingId
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

    private void validateTrustedListings(
            List<ListingResponseDto> listings,
            String operation
    ) {
        if (listings == null) {
            throw new IllegalStateException(
                    "Backend returned null listing list while attempting to "
                            + operation
            );
        }

        for (ListingResponseDto listing : listings) {
            validateTrustedListing(listing, operation);
        }
    }

    private void validateTrustedListing(
            ListingResponseDto listing,
            String operation
    ) {
        if (listing == null) {
            throw new IllegalStateException(
                    "Backend returned a null listing while attempting to "
                            + operation
            );
        }

        try {
            MarketplaceUrls.resolveVintedListingUrl(
                    listing.url(),
                    listing.listingId()
            );

            boolean hasConversationId =
                    listing.conversationId() != null
                            && !listing.conversationId().isBlank();
            boolean hasConversationUrl =
                    listing.conversationUrl() != null
                            && !listing.conversationUrl().isBlank();

            if (hasConversationId != hasConversationUrl) {
                throw new IllegalArgumentException(
                        "Conversation id and URL must either both be present or both be absent"
                );
            }

            if ("NEGOTIATING".equals(listing.status())
                    && !hasConversationId) {
                throw new IllegalArgumentException(
                        "NEGOTIATING listing has no conversation reference"
                );
            }

            if (hasConversationId) {
                MarketplaceUrls.resolveVintedConversationUrl(
                        listing.conversationUrl(),
                        listing.conversationId()
                );
            }
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "Backend returned an untrusted or mismatched marketplace listing while attempting to "
                            + operation
                            + ". backendListingId="
                            + listing.id()
                            + ", marketplaceListingId="
                            + listing.listingId()
                            + ", url="
                            + listing.url()
                            + ", conversationId="
                            + listing.conversationId()
                            + ", conversationUrl="
                            + listing.conversationUrl(),
                    exception
            );
        }
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
