package pl.flipbot.negotiation.audit;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.flipbot.negotiation.audit.dto.RealActionAuditResponse;
import pl.flipbot.negotiation.audit.dto.UpsertRealActionAuditRequest;

import java.util.List;

@RestController
@RequestMapping("/api/bots/{botId}")
@RequiredArgsConstructor
public class RealActionAuditController {

    private final RealActionAuditService realActionAuditService;

    @PostMapping("/listings/{listingId}/real-action-audit")
    public ResponseEntity<RealActionAuditResponse> record(
            @PathVariable Long botId,
            @PathVariable Long listingId,
            @Valid @RequestBody UpsertRealActionAuditRequest request
    ) {
        return ResponseEntity.ok(
                realActionAuditService.record(
                        botId,
                        listingId,
                        request
                )
        );
    }

    @GetMapping("/real-action-audits")
    public ResponseEntity<List<RealActionAuditResponse>> getForBot(
            @PathVariable Long botId
    ) {
        return ResponseEntity.ok(
                realActionAuditService.getForBot(botId)
        );
    }
}
