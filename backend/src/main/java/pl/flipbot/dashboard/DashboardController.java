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
            DashboardPeriod period
    ) {

        return ResponseEntity.ok(
                dashboardStatsService.getStats(
                        period
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
