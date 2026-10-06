package pl.flipbot.listing;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.flipbot.listing.dto.*;
import pl.flipbot.negotiation.NegotiationCapacityService;
import pl.flipbot.negotiation.dto.NegotiationCapacityResponse;

import java.util.List;

@RestController
@RequestMapping(
        "/api/bots/{botId}/listings"
)
@RequiredArgsConstructor
public class BotListingController {

    private final ListingService listingService;

    private final BotTargetListingService botTargetListingService;

    private final NegotiationCapacityService
            negotiationCapacityService;

    @PostMapping("/discover")
    public ResponseEntity<List<ListingResponse>>
    discoverListings(
            @PathVariable Long botId,
            @Valid
            @RequestBody
            DiscoverListingsRequest request
    ) {

        List<ListingResponse> claimedListings =
                botTargetListingService.discoverPrimary(
                        botId,
                        request
                );

        return ResponseEntity.ok(
                claimedListings
        );

    }

    @GetMapping("/discovered")
    public ResponseEntity<List<ListingResponse>>
    getDiscoveredListings(
            @PathVariable Long botId
    ) {

        return ResponseEntity.ok(
                listingService.getDiscoveredListings(
                        botId
                )
        );

    }

    @GetMapping("/negotiating")
    public ResponseEntity<List<ListingResponse>>
    getNegotiatingListings(
            @PathVariable Long botId
    ) {

        return ResponseEntity.ok(
                listingService.getNegotiatingListings(
                        botId
                )
        );

    }

    @GetMapping("/recovery-candidates")
    public ResponseEntity<List<ListingResponse>>
    getNegotiationRecoveryCandidates(
            @PathVariable Long botId
    ) {
        return ResponseEntity.ok(
                listingService.getNegotiationRecoveryCandidates(botId)
        );
    }

    @GetMapping("/action-required")
    public ResponseEntity<List<ListingResponse>>
    getActionRequiredListings(
            @PathVariable Long botId
    ) {

        return ResponseEntity.ok(
                listingService.getActionRequiredListings(
                        botId
                )
        );

    }

    @GetMapping("/purchased")
    public ResponseEntity<List<ListingResponse>>
    getPurchasedListings(
            @PathVariable Long botId
    ) {

        return ResponseEntity.ok(
                listingService.getPurchasedListings(
                        botId
                )
        );
    }

    @GetMapping("/skipped-by-user")
    public ResponseEntity<List<ListingResponse>>
    getSkippedByUserListings(
            @PathVariable Long botId
    ) {

        return ResponseEntity.ok(
                listingService.getSkippedByUserListings(
                        botId
                )
        );
    }

    @PatchMapping("/{listingId}/purchased")
    public ResponseEntity<ListingResponse>
    markAsPurchased(
            @PathVariable Long botId,
            @PathVariable Long listingId
    ) {

        return ResponseEntity.ok(
                listingService.markAsPurchased(
                        botId,
                        listingId
                )
        );
    }

    @PatchMapping("/{listingId}/skip")
    public ResponseEntity<ListingResponse>
    skipByUser(
            @PathVariable Long botId,
            @PathVariable Long listingId
    ) {

        return ResponseEntity.ok(
                listingService.skipByUser(
                        botId,
                        listingId
                )
        );
    }


    @GetMapping("/negotiation-capacity")
    public ResponseEntity<NegotiationCapacityResponse>
    getNegotiationCapacity(
            @PathVariable Long botId
    ) {

        return ResponseEntity.ok(
                negotiationCapacityService.calculateCapacity(
                        botId
                )
        );

    }

    @PatchMapping("/{listingId}")
    public ResponseEntity<ListingResponse>
    updateListing(
            @PathVariable Long botId,
            @PathVariable Long listingId,
            @Valid
            @RequestBody
            UpdateListingRequest request
    ) {

        return ResponseEntity.ok(
                listingService.updateListing(
                        botId,
                        listingId,
                        request
                )
        );

    }

    @PatchMapping("/{listingId}/recovery-checked")
    public ResponseEntity<ListingResponse>
    markNegotiationRecoveryChecked(
            @PathVariable Long botId,
            @PathVariable Long listingId
    ) {
        return ResponseEntity.ok(
                listingService.markNegotiationRecoveryChecked(
                        botId,
                        listingId
                )
        );
    }

    @PatchMapping("/{listingId}/reopen-negotiation")
    public ResponseEntity<ListingResponse>
    reopenNegotiationForRecovery(
            @PathVariable Long botId,
            @PathVariable Long listingId,
            @RequestBody ReopenNegotiationRequest request
    ) {
        return ResponseEntity.ok(
                listingService.reopenNegotiationForRecovery(
                        botId,
                        listingId,
                        request
                )
        );
    }

    @PatchMapping("/{listingId}/conversation")
    public ResponseEntity<ListingResponse>
    updateConversationIdentity(
            @PathVariable Long botId,
            @PathVariable Long listingId,
            @Valid
            @RequestBody
            UpdateConversationIdentityRequest request
    ) {
        return ResponseEntity.ok(
                listingService.updateConversationIdentity(
                        botId,
                        listingId,
                        request
                )
        );
    }

    @PatchMapping("/{listingId}/negotiation-activity")
    public ResponseEntity<NegotiationActivityResponse>
    recordNegotiationActivity(
            @PathVariable Long botId,
            @PathVariable Long listingId,
            @RequestBody
            NegotiationActivityRequest request
    ) {

        return ResponseEntity.ok(
                listingService.recordNegotiationActivity(
                        botId,
                        listingId,
                        request
                )
        );
    }

}
