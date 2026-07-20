package pl.flipbot.bot.dto;

import lombok.Builder;
import lombok.Getter;
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

    private String model;

    private BigDecimal minPrice;

    private BigDecimal maxPrice;

    private List<NegotiationStepResponse> negotiationSteps;
}
