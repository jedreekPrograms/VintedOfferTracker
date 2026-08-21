package pl.flipbot.negotiation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OfferSentRequest {

    @NotNull
    private Long botId;

    @NotBlank
    private String listingId;
}
