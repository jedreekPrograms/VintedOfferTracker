package pl.flipbot.marketstats;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/market-stats/models/{modelId}")
@RequiredArgsConstructor
public class MarketStatsScanCompletenessController {

    private final MarketStatsScanCompletenessService scanCompletenessService;

    @GetMapping("/full-catalog-scan-required")
    public boolean isFullCatalogScanRequired(
            @PathVariable Long modelId
    ) {
        return scanCompletenessService.requiresFullCatalogScan(modelId);
    }
}
