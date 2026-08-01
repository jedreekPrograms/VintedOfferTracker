package pl.flipbot.mapper;

import org.springframework.stereotype.Component;
import pl.flipbot.listing.Listing;
import pl.flipbot.listing.dto.ListingResponse;

@Component
public class ListingMapper {

    public ListingResponse map(
            Listing listing
    ) {

        return ListingResponse.builder()
                .id(
                        listing.getId()
                )
                .listingId(
                        listing.getListingId()
                )
                .title(
                        listing.getTitle()
                )
                .url(
                        listing.getUrl()
                )
                .originalPrice(
                        listing.getOriginalPrice()
                )
                .currentPrice(
                        listing.getCurrentPrice()
                )
                .currentStep(
                        listing.getCurrentStep()
                )
                .awaitingSellerResponse(
                        listing.getAwaitingSellerResponse()
                )
                .conversationId(
                        listing.getConversationId()
                )
                .conversationUrl(
                        listing.getConversationUrl()
                )
                .status(
                        listing.getStatus().name()
                )
                .build();

    }

}