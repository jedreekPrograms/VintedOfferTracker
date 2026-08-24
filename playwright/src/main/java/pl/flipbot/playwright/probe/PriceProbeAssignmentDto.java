package pl.flipbot.playwright.probe;

import java.math.BigDecimal;

public record PriceProbeAssignmentDto(
        Long probeId,
        Long sourceListingBackendId,
        String marketplaceListingId,
        String title,
        String listingUrl,
        BigDecimal referenceOfferPrice,
        BigDecimal probePrice,
        String message,
        int probeNumber,
        int maximumProbeCount
) {
}
