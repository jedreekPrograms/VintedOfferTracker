package pl.flipbot.dashboard;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
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
}
