package pl.flipbot.playwright.api.quota.dto;

public record OfferQuotaReservationResponseDto(
        boolean reserved,
        int limit,
        int used,
        int remaining
) {
}