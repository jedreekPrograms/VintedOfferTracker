package pl.flipbot.negotiation.quota;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.flipbot.negotiation.quota.dto.DailyOfferQuotaResponse;
import pl.flipbot.negotiation.quota.dto.OfferQuotaReservationRequest;
import pl.flipbot.negotiation.quota.dto.OfferQuotaReservationResponse;

@RestController
@RequestMapping("/api/bots/{botId}/offer-quota")
@RequiredArgsConstructor
public class DailyOfferQuotaController {

    private final DailyOfferQuotaService dailyOfferQuotaService;

    @GetMapping
    public ResponseEntity<DailyOfferQuotaResponse> getQuota(
            @PathVariable Long botId
    ) {
        return ResponseEntity.ok(dailyOfferQuotaService.getQuota(botId));
    }

    @PostMapping("/reserve")
    public ResponseEntity<OfferQuotaReservationResponse> reserveSlot(
            @PathVariable Long botId,
            @RequestBody OfferQuotaReservationRequest request
    ) {
        return ResponseEntity.ok(
                dailyOfferQuotaService.reserveSlot(botId, request.requestId())
        );
    }

    @PostMapping("/release")
    public ResponseEntity<DailyOfferQuotaResponse> releaseSlot(
            @PathVariable Long botId,
            @RequestBody OfferQuotaReservationRequest request
    ) {
        return ResponseEntity.ok(
                dailyOfferQuotaService.releaseSlot(botId, request.requestId())
        );
    }
}
