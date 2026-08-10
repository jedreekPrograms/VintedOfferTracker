package pl.flipbot.listing.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class ListingHistoryResponse {

    private Long id;

    private String listingId;

    private String title;

    private String url;

    private BigDecimal originalPrice;

    private BigDecimal currentPrice;

    private Integer currentStep;

    private String status;

    private LocalDateTime decisionAt;

    private Long botId;

    private String botName;
}