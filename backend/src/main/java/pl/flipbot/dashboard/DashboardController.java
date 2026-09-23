package pl.flipbot.dashboard;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.flipbot.bot.runtime.BotSessionPreviewService;
import pl.flipbot.dashboard.dto.DashboardStatsResponse;
import pl.flipbot.dashboard.dto.RuntimeDashboardResponse;
import pl.flipbot.listing.HistoryOutcome;
import pl.flipbot.listing.OfferAssessment;

import java.util.Set;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardStatsService
            dashboardStatsService;

    private final RuntimeDashboardService
            runtimeDashboardService;

    private final BotSessionPreviewService
            sessionPreviewService;

    @GetMapping("/stats")
    public ResponseEntity<DashboardStatsResponse>
    getStats(
            @RequestParam(
                    defaultValue = "ALL"
            )
            DashboardPeriod period,
            @RequestParam(required = false)
            Set<HistoryOutcome> outcomes,
            @RequestParam(required = false)
            Set<OfferAssessment> assessments
    ) {

        return ResponseEntity.ok(
                dashboardStatsService.getStats(
                        period,
                        outcomes == null ? Set.of() : outcomes,
                        assessments == null ? Set.of() : assessments
                )
        );
    }

    @GetMapping("/runtime")
    public ResponseEntity<RuntimeDashboardResponse>
    getRuntimeDashboard() {

        return ResponseEntity.ok(
                runtimeDashboardService.getRuntimeDashboard()
        );
    }

    @PutMapping("/runtime/{botId}/session-preview")
    public ResponseEntity<Void> setSessionPreview(
            @PathVariable Long botId,
            @RequestParam boolean enabled
    ) {
        sessionPreviewService.setPreviewRequested(botId, enabled);
        return ResponseEntity.noContent().build();
    }
}