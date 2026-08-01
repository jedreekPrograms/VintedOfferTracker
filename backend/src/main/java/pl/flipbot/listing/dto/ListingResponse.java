package pl.flipbot.listing.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

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

}