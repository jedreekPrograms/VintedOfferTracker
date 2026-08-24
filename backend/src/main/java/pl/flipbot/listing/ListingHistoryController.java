package pl.flipbot.listing;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.flipbot.listing.dto.ListingHistoryResponse;
import pl.flipbot.listing.dto.UpdateHistoryPurchasePriceRequest;

import java.util.List;

@RestController
@RequestMapping("/api/listings/history")
@RequiredArgsConstructor
public class ListingHistoryController {

    private final ListingHistoryService listingHistoryService;

    @GetMapping
    public ResponseEntity<List<ListingHistoryResponse>> getHistory() {

        return ResponseEntity.ok(
                listingHistoryService.getHistory()
        );
    }

    @PatchMapping("/{listingId}/purchase-price")
    public ResponseEntity<ListingHistoryResponse> updatePurchasePrice(
            @PathVariable Long listingId,
            @Valid @RequestBody UpdateHistoryPurchasePriceRequest request
    ) {

        return ResponseEntity.ok(
                listingHistoryService.updatePurchasePrice(
                        listingId,
                        request.purchasePrice()
                )
        );
    }

    @DeleteMapping("/{listingId}")
    public ResponseEntity<Void> hideHistoryEntry(
            @PathVariable Long listingId
    ) {

        listingHistoryService.hideHistoryEntry(listingId);
        return ResponseEntity.noContent().build();
    }
}
