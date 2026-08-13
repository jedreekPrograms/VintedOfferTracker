package pl.flipbot.negotiation.guard;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.flipbot.negotiation.guard.dto.AcquireRealActionGuardRequest;
import pl.flipbot.negotiation.guard.dto.RealActionGuardResponse;
import pl.flipbot.negotiation.guard.dto.ReleaseRealActionGuardRequest;
import pl.flipbot.negotiation.guard.dto.ReleaseRealActionGuardResponse;

@RestController
@RequestMapping(
        "/api/bots/{botId}/listings/{listingId}/real-action-guard"
)
@RequiredArgsConstructor
public class RealActionGuardController {

    private final RealActionGuardService realActionGuardService;

    @PostMapping("/acquire")
    public ResponseEntity<RealActionGuardResponse> acquire(
            @PathVariable Long botId,
            @PathVariable Long listingId,
            @Valid @RequestBody AcquireRealActionGuardRequest request
    ) {
        return ResponseEntity.ok(
                realActionGuardService.acquire(
                        botId,
                        listingId,
                        request
                )
        );
    }

    @PostMapping("/release")
    public ResponseEntity<ReleaseRealActionGuardResponse> release(
            @PathVariable Long botId,
            @PathVariable Long listingId,
            @Valid @RequestBody ReleaseRealActionGuardRequest request
    ) {
        return ResponseEntity.ok(
                realActionGuardService.release(
                        botId,
                        listingId,
                        request
                )
        );
    }
}
