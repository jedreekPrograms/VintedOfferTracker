package pl.flipbot.analytics;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.flipbot.analytics.dto.AnalyticsOverviewResponse;
import pl.flipbot.dashboard.DashboardPeriod;
import pl.flipbot.listing.HistoryOutcome;
import pl.flipbot.listing.OfferAssessment;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/overview")
    public ResponseEntity<AnalyticsOverviewResponse> getOverview(
            @RequestParam(defaultValue = "ALL")
            DashboardPeriod period,
            @RequestParam(required = false)
            Long modelId,
            @RequestParam(required = false)
            Set<Long> modelIds,
            @RequestParam(required = false)
            Set<HistoryOutcome> outcomes,
            @RequestParam(required = false)
            Set<OfferAssessment> assessments,
            @RequestParam(defaultValue = "ALL")
            AnalyticsSource source,
            @RequestParam(defaultValue = "DAY")
            AnalyticsGranularity granularity,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to
    ) {
        Set<Long> selectedModelIds = new LinkedHashSet<>();

        if (modelIds != null) {
            selectedModelIds.addAll(modelIds);
        }

        if (modelId != null) {
            selectedModelIds.add(modelId);
        }

        return ResponseEntity.ok(
                analyticsService.getOverview(
                        period,
                        Set.copyOf(selectedModelIds),
                        outcomes == null ? Set.of() : outcomes,
                        assessments == null ? Set.of() : assessments,
                        source,
                        granularity,
                        from,
                        to
                )
        );
    }
}
