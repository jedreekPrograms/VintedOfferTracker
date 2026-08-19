package pl.flipbot.listing.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class ListingResponse {

    private Long id;

    private String listingId;

    private String title;

    private String url;

    private BigDecimal originalPrice;

    private BigDecimal currentPrice;

    private Integer currentStep;

    private Boolean awaitingSellerResponse;

    private String conversationId;

    private String conversationUrl;

    private String status;

    private LocalDateTime decisionAt;

    private LocalDateTime currentStepStartedAt;

    private LocalDateTime sellerActivityAt;

    private LocalDateTime readDetectedAt;

    private String formalResponseFingerprint;

    private LocalDateTime formalResponseDetectedAt;
}
