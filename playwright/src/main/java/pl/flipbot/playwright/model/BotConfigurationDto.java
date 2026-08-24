package pl.flipbot.playwright.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class BotConfigurationDto {

    private String marketplace;

    private List<String> categoryPath;

    private String brand;

    /*
     * VINTED_MODEL albo SEARCH_QUERY.
     */
    private String targetMode;

    private String model;

    private String searchQuery;

    private BigDecimal minPrice;

    private BigDecimal maxPrice;

    private Boolean autoRaiseOfferToVintedMinimum;

    private BigDecimal maxAutomaticOffer;

    private Integer dailyNegotiationBudget;

    private List<NegotiationStepDto> negotiationSteps;

    /**
     * Runtime-only negotiation semantic resolved from the per-listing snapshot.
     * Live bot responses may leave this null because the first offer does not
     * need it; active conversations resolve it before making another action.
     */
    private String negotiationPricingMode;
}
