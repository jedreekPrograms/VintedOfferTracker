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
     *
     * Na etapie 3A Playwright tylko odbiera tę wartość.
     * Obsługę SEARCH_QUERY dodamy w kolejnym etapie.
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
}
