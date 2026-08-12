package pl.flipbot.bot.dto;

import lombok.Builder;
import lombok.Getter;
import pl.flipbot.bot.configuration.TargetMode;
import pl.flipbot.marketplace.Marketplace;
import pl.flipbot.negotiation.dto.NegotiationStepResponse;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class BotConfigurationResponse {

    private Marketplace marketplace;

    private List<String> categoryPath;

    private String brand;

    private TargetMode targetMode;

    private String model;

    private String searchQuery;

    private BigDecimal minPrice;

    private BigDecimal maxPrice;

    private Boolean autoRaiseOfferToVintedMinimum;

    private BigDecimal maxAutomaticOffer;

    private Integer dailyNegotiationBudget;

    private List<NegotiationStepResponse> negotiationSteps;
}
