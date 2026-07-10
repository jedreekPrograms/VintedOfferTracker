package pl.flipbot.bot.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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

    @Valid
    @NotNull
    private List<CreateNegotiationStepRequest> negotiationSteps;

}