package pl.flipbot.marketstats;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.flipbot.marketstats.dto.CreateMarketStatsObserverRequest;
import pl.flipbot.marketstats.dto.MarketStatsObserverPlaywrightResponse;
import pl.flipbot.marketstats.dto.MarketStatsObserverResponse;
import pl.flipbot.marketstats.dto.UpdateMarketStatsObserverRequest;

@RestController
@RequestMapping("/api/market-stats/observer")
@RequiredArgsConstructor
public class MarketStatsObserverController {

    private final MarketStatsObserverService observerService;

    @GetMapping
    public ResponseEntity<MarketStatsObserverResponse> getObserver() {
        return observerService.getObserver()
                .map(ResponseEntity::ok)
                .orElseGet(
                        () -> ResponseEntity.noContent().build()
                );
    }

    @PostMapping
    public MarketStatsObserverResponse createObserver(
            @Valid @RequestBody CreateMarketStatsObserverRequest request
    ) {
        return observerService.createObserver(request);
    }

    @PatchMapping
    public MarketStatsObserverResponse updateObserver(
            @Valid @RequestBody UpdateMarketStatsObserverRequest request
    ) {
        return observerService.updateObserver(request);
    }

    @DeleteMapping
    public void deleteObserver() {
        observerService.deleteObserver();
    }

    @GetMapping("/playwright")
    public MarketStatsObserverPlaywrightResponse getObserverForPlaywright() {
        return observerService.getObserverForPlaywright();
    }
}
