package pl.flipbot.negotiation.quota.dto;

public record OfferQuotaReservationResponse(
        boolean reserved,
        int limit,
        int used,
        int remaining
) {
}