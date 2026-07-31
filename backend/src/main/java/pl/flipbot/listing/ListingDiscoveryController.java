package pl.flipbot.listing;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.flipbot.listing.dto.DiscoverListingsRequest;
import pl.flipbot.listing.dto.ListingResponse;

import java.util.List;

@RestController
@RequestMapping(
        "/api/bots/{botId}/listings"
)
@RequiredArgsConstructor
public class ListingDiscoveryController {

    private final ListingService listingService;

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

}