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
    private final MarketStatsHealthService healthService;

    @GetMapping("/planning")
    public List<CalendarModelPlanningResponse> getPlanning() {
        return calendarPlanningService.getPlanning();
    }

    @GetMapping("/targets")
    public List<MarketStatsTargetResponse> getTargets() {
        return marketStatsService.getTargets();
    }

    @GetMapping("/health")
    public MarketStatsHealthResponse getHealth() {
        return healthService.getHealth();
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
}
