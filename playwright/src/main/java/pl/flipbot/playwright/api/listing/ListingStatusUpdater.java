package pl.flipbot.playwright.api.listing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.listing.ListingClient;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.api.listing.dto.UpdateListingRequestDto;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.negotiation.NegotiationDecision;

import java.math.BigDecimal;
import java.util.Objects;

@Slf4j
@RequiredArgsConstructor
public class ListingStatusUpdater {

    private final BotContext context;

    private final ListingClient listingClient;


    public ListingResponseDto markActionRequired(
            ListingResponseDto listing,
            NegotiationDecision decision
    ) {

        BigDecimal price =
                resolveDecisionPrice(
                        listing,
                        decision
                );


        ListingResponseDto updatedListing =
                updateNegotiationStatus(
                        listing,
                        "ACTION_REQUIRED",
                        price
                );


        if (
                !"ACTION_REQUIRED".equals(
                        updatedListing.status()
                )
        ) {

            throw new IllegalStateException(
                    "Backend returned an unexpected status. Expected "
                            + "ACTION_REQUIRED, actual: "
                            + updatedListing.status()
            );
        }


        return updatedListing;
    }


    public ListingResponseDto markRejected(
            ListingResponseDto listing,
            NegotiationDecision decision
    ) {

        BigDecimal price =
                resolveDecisionPrice(
                        listing,
                        decision
                );


        ListingResponseDto updatedListing =
                updateNegotiationStatus(
                        listing,
                        "REJECTED",
                        price
                );


        if (
                !"REJECTED".equals(
                        updatedListing.status()
                )
        ) {

            throw new IllegalStateException(
                    "Backend returned an unexpected status. Expected "
                            + "REJECTED, actual: "
                            + updatedListing.status()
            );
        }


        return updatedListing;
    }


    public ListingResponseDto markExpired(
            ListingResponseDto listing
    ) {

        BigDecimal price =
                listing.currentPrice()
                        != null
                        ? listing.currentPrice()
                        : listing.originalPrice();


        if (price == null) {

            throw new IllegalStateException(
                    "Cannot mark backend listing "
                            + listing.id()
                            + " as EXPIRED because its price is null"
            );
        }


        ListingResponseDto updatedListing =
                updateNegotiationStatus(
                        listing,
                        "EXPIRED",
                        price
                );


        if (
                !"EXPIRED".equals(
                        updatedListing.status()
                )
        ) {

            throw new IllegalStateException(
                    "Backend returned an unexpected status. Expected "
                            + "EXPIRED, actual: "
                            + updatedListing.status()
            );
        }


        return updatedListing;
    }


    public ListingResponseDto markNegotiationUnavailable(
            ListingResponseDto listing
    ) {

        BigDecimal price =
                listing.currentPrice()
                        != null
                        ? listing.currentPrice()
                        : listing.originalPrice();


        if (price == null) {

            throw new IllegalStateException(
                    "Cannot mark backend listing "
                            + listing.id()
                            + " as UNAVAILABLE because its price is null"
            );
        }


        ListingResponseDto updatedListing =
                updateNegotiationStatus(
                        listing,
                        "UNAVAILABLE",
                        price
                );


        if (
                !"UNAVAILABLE".equals(
                        updatedListing.status()
                )
        ) {

            throw new IllegalStateException(
                    "Backend returned an unexpected status. Expected "
                            + "UNAVAILABLE, actual: "
                            + updatedListing.status()
            );
        }


        return updatedListing;
    }


    public void markUnavailable(
            Long botId,
            ListingResponseDto listing
    ) {

        UpdateListingRequestDto request =
                createDiscoveredStatusUpdateRequest(
                        listing,
                        "UNAVAILABLE"
                );


        ListingResponseDto updatedListing =
                listingClient.updateListing(
                        botId,
                        listing.id(),
                        request
                );


        log.info(
                "Backend listing {} was marked as {}",
                updatedListing.id(),
                updatedListing.status()
        );
    }


    public void markOfferTooLow(
            Long botId,
            ListingResponseDto listing
    ) {

        UpdateListingRequestDto request =
                createDiscoveredStatusUpdateRequest(
                        listing,
                        "SKIPPED_OFFER_TOO_LOW"
                );


        ListingResponseDto updatedListing =
                listingClient.updateListing(
                        botId,
                        listing.id(),
                        request
                );


        log.info(
                "Backend listing {} was marked as {}",
                updatedListing.id(),
                updatedListing.status()
        );
    }


    public void markOutsidePriceRange(
            Long botId,
            ListingResponseDto listing
    ) {

        UpdateListingRequestDto request =
                createDiscoveredStatusUpdateRequest(
                        listing,
                        "SKIPPED_OUTSIDE_PRICE_RANGE"
                );


        ListingResponseDto updatedListing =
                listingClient.updateListing(
                        botId,
                        listing.id(),
                        request
                );


        log.info(
                "[PRICE GUARD] Backend listing {} was marked as {}. "
                        + "Original price: {}.",
                updatedListing.id(),
                updatedListing.status(),
                listing.originalPrice()
        );
    }


    private ListingResponseDto updateNegotiationStatus(
            ListingResponseDto listing,
            String status,
            BigDecimal currentPrice
    ) {

        if (
                listing.currentStep() == null
                        || listing.currentStep() <= 0
        ) {

            throw new IllegalStateException(
                    "Cannot update negotiation status because backend "
                            + "listing "
                            + listing.id()
                            + " has an invalid current step: "
                            + listing.currentStep()
            );
        }


        UpdateListingRequestDto request =
                new UpdateListingRequestDto(
                        status,
                        currentPrice,
                        listing.currentStep(),
                        false,
                        listing.conversationId(),
                        listing.conversationUrl()
                );


        ListingResponseDto updatedListing =
                listingClient.updateListing(
                        context.getBot().getId(),
                        listing.id(),
                        request
                );


        if (
                Boolean.TRUE.equals(
                        updatedListing.awaitingSellerResponse()
                )
        ) {

            throw new IllegalStateException(
                    "Backend listing "
                            + listing.id()
                            + " still has awaitingSellerResponse=true "
                            + "after changing status to "
                            + status
            );
        }


        if (
                !Objects.equals(
                        listing.currentStep(),
                        updatedListing.currentStep()
                )
        ) {

            throw new IllegalStateException(
                    "Backend changed the current negotiation step "
                            + "unexpectedly. Expected: "
                            + listing.currentStep()
                            + ", actual: "
                            + updatedListing.currentStep()
            );
        }


        if (
                !Objects.equals(
                        listing.conversationId(),
                        updatedListing.conversationId()
                )
        ) {

            throw new IllegalStateException(
                    "Backend changed the conversation ID unexpectedly. "
                            + "Expected: "
                            + listing.conversationId()
                            + ", actual: "
                            + updatedListing.conversationId()
            );
        }


        if (
                updatedListing.currentPrice() == null
                        || updatedListing.currentPrice()
                        .compareTo(
                                currentPrice
                        ) != 0
        ) {

            throw new IllegalStateException(
                    "Backend returned an unexpected current price. "
                            + "Expected: "
                            + currentPrice
                            + ", actual: "
                            + updatedListing.currentPrice()
            );
        }


        return updatedListing;
    }


    private BigDecimal resolveDecisionPrice(
            ListingResponseDto listing,
            NegotiationDecision decision
    ) {

        if (
                decision.sellerCounterOfferPrice()
                        != null
        ) {

            return decision
                    .sellerCounterOfferPrice();
        }


        if (listing.currentPrice() != null) {

            return listing.currentPrice();
        }


        if (listing.originalPrice() != null) {

            return listing.originalPrice();
        }


        throw new IllegalStateException(
                "Cannot determine decision price for backend listing "
                        + listing.id()
        );
    }


    private UpdateListingRequestDto
    createDiscoveredStatusUpdateRequest(
            ListingResponseDto listing,
            String status
    ) {

        BigDecimal currentPrice =
                listing.currentPrice() != null
                        ? listing.currentPrice()
                        : listing.originalPrice();


        if (currentPrice == null) {

            throw new IllegalStateException(
                    "Cannot update backend listing "
                            + listing.id()
                            + " because its price is null"
            );
        }


        Integer currentStep =
                listing.currentStep() != null
                        ? listing.currentStep()
                        : 0;


        return new UpdateListingRequestDto(
                status,
                currentPrice,
                currentStep,
                false,
                null,
                null
        );
    }
}