package pl.flipbot.negotiation.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OfferSentRequest {

    @NotBlank
    private String listingId;
}
