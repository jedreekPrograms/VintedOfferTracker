package pl.flipbot.listing;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.flipbot.listing.dto.ActionRequiredCountResponse;
import pl.flipbot.listing.dto.ActionRequiredListingResponse;

import java.util.List;

@RestController
@RequestMapping("/api/listings/action-required")
@RequiredArgsConstructor
public class ActionRequiredListingController {

    private final ActionRequiredListingService service;

    @GetMapping
    public ResponseEntity<List<ActionRequiredListingResponse>> getAll() {
        return ResponseEntity.ok(
                service.getAll()
        );
    }

    @GetMapping("/count")
    public ResponseEntity<ActionRequiredCountResponse> count() {
        return ResponseEntity.ok(
                new ActionRequiredCountResponse(
                        service.count()
                )
        );
    }
}
