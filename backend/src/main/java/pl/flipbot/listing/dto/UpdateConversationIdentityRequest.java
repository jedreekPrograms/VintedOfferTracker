package pl.flipbot.listing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateConversationIdentityRequest(
        @NotBlank
        @Size(max = 255)
        String conversationId,

        @NotBlank
        @Size(max = 1000)
        String conversationUrl
) {
}
