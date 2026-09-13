package pl.flipbot.listing;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.flipbot.listing.dto.DiscoverListingsRequest;
import pl.flipbot.listing.dto.ListingResponse;

import java.util.List;

@RestController
@RequestMapping("/api/bots/{botId}/listings")
@RequiredArgsConstructor
public class BotTargetListingController {

    private final BotTargetListingService service;

    @GetMapping("/discovered/primary")
    public ResponseEntity<List<ListingResponse>> getPrimaryDiscovered(
            @PathVariable Long botId
    ) {
        return ResponseEntity.ok(service.getPrimaryDiscovered(botId));
    }

    @GetMapping("/discovered/additional/{targetId}")
    public ResponseEntity<List<ListingResponse>> getAdditionalTargetDiscovered(
            @PathVariable Long botId,
            @PathVariable Long targetId
    ) {
        return ResponseEntity.ok(
                service.getAdditionalTargetDiscovered(botId, targetId)
        );
    }

    @PostMapping("/discover/additional/{targetId}")
    public ResponseEntity<List<ListingResponse>> discoverAdditionalTarget(
            @PathVariable Long botId,
            @PathVariable Long targetId,
            @Valid @RequestBody DiscoverListingsRequest request
    ) {
        return ResponseEntity.ok(
                service.discoverAdditionalTarget(botId, targetId, request)
        );
    }
}
