package pl.flipbot.marketstats;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pl.flipbot.marketstats.dto.*;

import java.util.List;

@RestController
@RequestMapping("/api/market-stats")
@RequiredArgsConstructor
public class MarketStatsController {

    private final MarketStatsService marketStatsService;
    private final MarketStatsCalendarPlanningService calendarPlanningService;
    private final MarketStatsScanTriggerService scanTriggerService;
    private final MarketListingPublicationService listingPublicationService;

    @GetMapping("/planning")
    public List<CalendarModelPlanningResponse> getPlanning() {
        return calendarPlanningService.getPlanning();
    }

    @GetMapping("/targets")
    public List<MarketStatsTargetResponse> getTargets() {
        return marketStatsService.getTargets();
    }

    @GetMapping("/scan-needed")
    public boolean isScanNeeded() {
        return scanTriggerService.isScanNeeded();
    }

    @GetMapping("/models/{modelId}/known-listing-ids")
    public KnownMarketListingIdsResponse getKnownListingIds(
            @PathVariable Long modelId
    ) {
        return marketStatsService.getKnownListingIds(modelId);
    }

    @GetMapping("/models/{modelId}/missing-publication-listing-ids")
    public List<String> getMissingPublicationListingIds(
            @PathVariable Long modelId
    ) {
        return listingPublicationService.getMissingPublicationListingIds(modelId);
    }

    @PostMapping("/models/{modelId}/observations")
    public MarketObservationBatchResponse recordObservations(
            @PathVariable Long modelId,
            @Valid @RequestBody MarketObservationBatchRequest request
    ) {
        return marketStatsService.recordObservations(
                modelId,
                request
        );
    }

    @PostMapping("/models/{modelId}/publication-times")
    public int recordPublicationTimes(
            @PathVariable Long modelId,
            @Valid @RequestBody MarketListingPublicationBatchRequest request
    ) {
        return listingPublicationService.recordPublicationTimes(
                modelId,
                request
        );
    }
}
