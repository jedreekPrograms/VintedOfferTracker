package pl.flipbot.negotiation.quota;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.flipbot.negotiation.quota.dto.DailyOfferQuotaResponse;
import pl.flipbot.negotiation.quota.dto.OfferQuotaReservationResponse;

@RestController
@RequestMapping(
        "/api/bots/{botId}/offer-quota"
)
@RequiredArgsConstructor
public class DailyOfferQuotaController {

    private final DailyOfferQuotaService
            dailyOfferQuotaService;


    @GetMapping
    public ResponseEntity<DailyOfferQuotaResponse>
    getQuota(
            @PathVariable Long botId
    ) {

        return ResponseEntity.ok(
                dailyOfferQuotaService.getQuota(
                        botId
                )
        );
    }


    @PostMapping("/reserve")
    public ResponseEntity<OfferQuotaReservationResponse>
    reserveSlot(
            @PathVariable Long botId
    ) {

        return ResponseEntity.ok(
                dailyOfferQuotaService.reserveSlot(
                        botId
                )
        );
    }


    @PostMapping("/release")
    public ResponseEntity<DailyOfferQuotaResponse>
    releaseSlot(
            @PathVariable Long botId
    ) {

        return ResponseEntity.ok(
                dailyOfferQuotaService.releaseSlot(
                        botId
                )
        );
    }
}