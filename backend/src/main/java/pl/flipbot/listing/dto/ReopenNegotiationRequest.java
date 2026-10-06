package pl.flipbot.listing.dto;

public record ReopenNegotiationRequest(
        boolean awaitingSellerResponse,
        String reason
) {
}
