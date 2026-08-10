package pl.flipbot.listing;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.flipbot.listing.dto.ListingHistoryResponse;

import java.util.List;

@RestController
@RequestMapping("/api/listings/history")
@RequiredArgsConstructor
public class ListingHistoryController {

    private final ListingHistoryService
            listingHistoryService;

    @GetMapping
    public ResponseEntity<List<ListingHistoryResponse>>
    getHistory() {

        return ResponseEntity.ok(
                listingHistoryService.getHistory()
        );
    }
}