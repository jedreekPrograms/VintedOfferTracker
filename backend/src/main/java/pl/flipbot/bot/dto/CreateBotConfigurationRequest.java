package pl.flipbot.bot.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import pl.flipbot.marketplace.Marketplace;
import pl.flipbot.negotiation.dto.CreateNegotiationStepRequest;
import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class CreateBotConfigurationRequest {

    @NotNull
    private Marketplace marketplace;

    @NotEmpty
    private List<String> categoryPath;

    @NotBlank
    private String brand;

    @NotBlank
    private String model;

    @NotNull
    private BigDecimal minPrice;

    @NotNull
    private BigDecimal maxPrice;

    @NotNull
    private Integer dailyNegotiationBudget;

    @Valid
    @NotNull
    private List<CreateNegotiationStepRequest> negotiationSteps;

}