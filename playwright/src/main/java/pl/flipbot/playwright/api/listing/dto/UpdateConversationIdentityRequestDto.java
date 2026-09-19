package pl.flipbot.playwright.api.listing.dto;

public record UpdateConversationIdentityRequestDto(
        String conversationId,
        String conversationUrl
) {
}
