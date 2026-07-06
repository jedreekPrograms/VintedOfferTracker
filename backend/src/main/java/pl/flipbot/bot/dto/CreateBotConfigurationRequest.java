package pl.flipbot.bot.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateBotConfigurationRequest {

    private String category;

    private String subCategory;

    private String brand;

    private String model;

    private Double minPrice;

    private Double maxPrice;

    private Double firstOffer;

    private Double maxOffer;

    private Double negotiationStep;

    private Integer maxNegotiationAttempts;

}