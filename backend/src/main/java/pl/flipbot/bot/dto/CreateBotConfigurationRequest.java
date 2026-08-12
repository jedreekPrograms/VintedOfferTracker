package pl.flipbot.bot.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import pl.flipbot.bot.configuration.TargetMode;
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

    /*
     * Pole celowo nie ma @NotNull.
     *
     * Dzięki temu obecny frontend może nadal tworzyć boty podczas
     * wdrażania etapu 3A. Brak wartości jest interpretowany przez
     * BotService jako VINTED_MODEL.
     */
    private TargetMode targetMode;

    /*
     * Wymagane przez BotService dla VINTED_MODEL.
     * Dla SEARCH_QUERY może być null.
     */
    private String model;

    /*
     * Wymagane przez BotService dla SEARCH_QUERY.
     * Dla VINTED_MODEL może być null.
     */
    private String searchQuery;

    @NotNull
    private BigDecimal minPrice;

    @NotNull
    private BigDecimal maxPrice;

    /*
     * Brak wartości oznacza false, aby zachować zgodność
     * z obecnym frontendem podczas wdrażania.
     */
    private Boolean autoRaiseOfferToVintedMinimum;

    /*
     * Wymagane tylko wtedy, gdy
     * autoRaiseOfferToVintedMinimum == true.
     */
    private BigDecimal maxAutomaticOffer;

    @NotNull
    private Integer dailyNegotiationBudget;

    @Valid
    @NotNull
    private List<CreateNegotiationStepRequest> negotiationSteps;
}
