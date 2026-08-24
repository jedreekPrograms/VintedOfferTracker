package pl.flipbot.probe.dto;

import java.math.BigDecimal;

public record PriceProbeAssignmentResponse(
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
