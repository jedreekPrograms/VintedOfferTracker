package pl.flipbot.negotiation.quota.dto;

public record DailyOfferQuotaResponse(
        int limit,
        int used,
        int remaining
) {
}