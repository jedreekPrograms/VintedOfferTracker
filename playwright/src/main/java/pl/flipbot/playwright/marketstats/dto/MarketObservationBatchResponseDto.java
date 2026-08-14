package pl.flipbot.playwright.marketstats.dto;

public record MarketObservationBatchResponseDto(
        Long modelId,
        boolean baselineCreated,
        int observedListings,
        int newListings,
        String scannedAt,
        boolean complete
) {
}
