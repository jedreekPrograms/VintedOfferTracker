package pl.flipbot.playwright.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class BotConfigurationDto {

    private String marketplace;

    private String category;

    private String subCategory;

    private String brand;

    private String model;

    private BigDecimal minPrice;

    private BigDecimal maxPrice;

    private Integer dailyNegotiationBudget;

    private List<NegotiationStepDto> negotiationSteps;

}