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
import pl.flipbot.listing.dto.DiscoverListingsRequest;
import pl.flipbot.listing.dto.ListingResponse;
import pl.flipbot.listing.dto.UpdateListingRequest;
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
                listingService.discoverListings(
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

}