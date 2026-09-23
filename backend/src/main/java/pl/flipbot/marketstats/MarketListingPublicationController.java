package pl.flipbot.marketstats;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.flipbot.marketstats.dto.MarketListingPublicationBatchRequest;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/market-stats/models/{modelId}")
@RequiredArgsConstructor
public class MarketListingPublicationController {

    private final MarketListingPublicationService publicationService;

    @GetMapping("/missing-publication-listing-ids")
    public List<String> getMissingPublicationListingIds(
            @PathVariable Long modelId
    ) {
        return publicationService.getMissingPublicationListingIds(modelId);
    }

    @GetMapping("/publication-times")
    public Map<String, String> getPublicationTimes(
            @PathVariable Long modelId
    ) {
        return publicationService.getPublicationTimes(modelId);
    }

    @PostMapping("/publication-times")
    public int recordPublicationTimes(
            @PathVariable Long modelId,
            @Valid @RequestBody MarketListingPublicationBatchRequest request
    ) {
        return publicationService.recordPublicationTimes(modelId, request);
    }
}
