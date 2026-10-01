package pl.flipbot.marketstats;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/market-stats/models/{modelId}")
@RequiredArgsConstructor
public class MarketStatsPublicationWindowController {

    private final MarketStatsPublicationWindowService publicationWindowService;

    @PostMapping("/publication-window-complete")
    public LocalDateTime markPublicationWindowComplete(
            @PathVariable Long modelId,
            @RequestParam Integer trackingGeneration
    ) {
        return publicationWindowService.markPublicationWindowComplete(
                modelId,
                trackingGeneration
        );
    }
}
