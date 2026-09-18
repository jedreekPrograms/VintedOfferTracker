package pl.flipbot.listing;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.flipbot.listing.dto.ActionRequiredListingResponse;
import pl.flipbot.listing.dto.ListingResponse;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ActionRequiredListingService {

    private final ListingRepository listingRepository;

    @Transactional(readOnly = true)
    public List<ActionRequiredListingResponse> getAll() {
        return listingRepository.findActionRequiredRows(
                        ListingStatus.ACTION_REQUIRED
                )
                .stream()
                .map(this::map)
                .toList();
    }

    @Transactional(readOnly = true)
    public long count() {
        return listingRepository.countByStatus(
                ListingStatus.ACTION_REQUIRED
        );
    }

    private ActionRequiredListingResponse map(
            ListingRepository.ActionRequiredRow row
    ) {
        ListingResponse listing = ListingResponse.builder()
                .id(row.getId())
                .listingId(row.getListingId())
                .title(row.getTitle())
                .url(row.getUrl())
                .originalPrice(row.getOriginalPrice())
                .currentPrice(row.getCurrentPrice())
                .currentStep(row.getCurrentStep())
                .awaitingSellerResponse(row.getAwaitingSellerResponse())
                .conversationId(row.getConversationId())
                .conversationUrl(row.getConversationUrl())
                .status(row.getStatus().name())
                .decisionAt(row.getDecisionAt())
                .currentStepStartedAt(row.getCurrentStepStartedAt())
                .sellerActivityAt(row.getSellerActivityAt())
                .readDetectedAt(row.getReadDetectedAt())
                .formalResponseFingerprint(row.getFormalResponseFingerprint())
                .formalResponseDetectedAt(row.getFormalResponseDetectedAt())
                .additionalTargetId(row.getAdditionalTargetId())
                .productTargetLabel(row.getProductTargetLabel())
                .build();

        return new ActionRequiredListingResponse(
                row.getBotId(),
                row.getBotName(),
                listing
        );
    }
}
