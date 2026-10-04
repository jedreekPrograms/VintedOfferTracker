package pl.flipbot.mapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.flipbot.listing.Listing;
import pl.flipbot.listing.dto.ListingResponse;
import pl.flipbot.negotiation.strategy.NegotiationStrategySnapshotService;

@Component
@RequiredArgsConstructor
public class ListingMapper {

    private final NegotiationStrategySnapshotService snapshotService;

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
                .decisionAt(
                        listing.getDecisionAt()
                )
                .currentStepStartedAt(
                        listing.getCurrentStepStartedAt()
                )
                .sellerActivityAt(
                        listing.getSellerActivityAt()
                )
                .readDetectedAt(
                        listing.getReadDetectedAt()
                )
                .formalResponseFingerprint(
                        listing.getFormalResponseFingerprint()
                )
                .formalResponseDetectedAt(
                        listing.getFormalResponseDetectedAt()
                )
                .additionalTargetId(
                        listing.getAdditionalTarget() == null
                                ? null
                                : listing.getAdditionalTarget().getId()
                )
                .productTargetLabel(
                        listing.getProductTargetLabel()
                )
                .negotiationStrategyVersion(
                        listing.getNegotiationStrategyVersion()
                )
                .negotiationStrategySnapshot(
                        snapshotService.read(listing)
                )
                .build();
    }
}
