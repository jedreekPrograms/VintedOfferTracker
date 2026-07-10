package pl.flipbot.bot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import pl.flipbot.marketplace.Marketplace;

import java.math.BigDecimal;

@Getter
@Setter
public class CreateBotConfigurationRequest {

    @NotNull
    private Marketplace marketplace;

    @NotBlank
    private String category;

    @NotBlank
    private String subCategory;

    @NotBlank
    private String brand;

    @NotBlank
    private String model;

    @NotNull
    private BigDecimal minPrice;

    @NotNull
    private BigDecimal maxPrice;

    @NotNull
    private BigDecimal firstOffer;

    @NotNull
    private BigDecimal maxOffer;

    @NotNull
    private BigDecimal negotiationStep;

    @NotNull
    private Integer maxNegotiationAttempts;

}