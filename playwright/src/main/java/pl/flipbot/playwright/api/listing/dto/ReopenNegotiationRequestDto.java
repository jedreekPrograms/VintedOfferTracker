package pl.flipbot.playwright.api.listing.dto;

public record ReopenNegotiationRequestDto(
        boolean awaitingSellerResponse,
        String reason
) {
}
