package pl.flipbot.probe;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.flipbot.probe.dto.PriceProbeAssignmentResponse;
import pl.flipbot.probe.dto.PriceProbeOutcomeRequest;
import pl.flipbot.probe.dto.PriceProbeOutcomeResponse;

@RestController
@RequestMapping("/api/price-probes/bots/{botId}")
@RequiredArgsConstructor
public class PriceProbeController {

    private final PriceProbeService priceProbeService;

    @PostMapping("/claim")
    public ResponseEntity<PriceProbeAssignmentResponse> claim(
            @PathVariable Long botId
    ) {
        return priceProbeService.claimNext(botId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PatchMapping("/{probeId}")
    public ResponseEntity<PriceProbeOutcomeResponse> complete(
            @PathVariable Long botId,
            @PathVariable Long probeId,
            @Valid @RequestBody PriceProbeOutcomeRequest request
    ) {
        return ResponseEntity.ok(
                priceProbeService.complete(botId, probeId, request)
        );
    }
}
