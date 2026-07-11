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

    private String status;
}
