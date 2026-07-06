package pl.flipbot.bot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateBotConfigurationRequest {

    @NotBlank
    private String category;

    @NotBlank
    private String subCategory;

    @NotBlank
    private String brand;

    @NotBlank
    private String model;

    @NotNull
    private Double minPrice;

    @NotNull
    private Double maxPrice;

    @NotNull
    private Double firstOffer;

    @NotNull
    private Double maxOffer;

    @NotNull
    private Double negotiationStep;

    @NotNull
    private Integer maxNegotiationAttempts;

}