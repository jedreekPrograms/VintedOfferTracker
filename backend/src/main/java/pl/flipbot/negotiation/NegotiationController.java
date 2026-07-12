package pl.flipbot.negotiation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.flipbot.negotiation.dto.NegotiationResultRequest;
import pl.flipbot.negotiation.dto.OfferSentRequest;

@RestController
@RequestMapping("/api/negotiations")
@RequiredArgsConstructor
public class NegotiationController {

    private final NegotiationService negotiationService;

    @PostMapping("/offer-sent")
    public void offerSent(@Valid @RequestBody OfferSentRequest request) {

        negotiationService.markOfferAsSent(
                request.getListingId()
        );
    }

    @PostMapping("/result")
    public NegotiationDecision processResult(
            @Valid @RequestBody NegotiationResultRequest request
    ) {

        return negotiationService.processNegotiationResult(
                request.getListingId(),
                request.getResult(),
                request.getCounterOffer()
        );
    }
}
