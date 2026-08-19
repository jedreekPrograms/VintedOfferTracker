package pl.flipbot.bot.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
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

    private TargetMode targetMode;

    private String model;

    private String searchQuery;

    @NotNull
    private BigDecimal minPrice;

    @NotNull
    private BigDecimal maxPrice;

    /**
     * When enabled, configured negotiation steps are treated as the base
     * percentage ladder. A too-low first offer may be lifted above Vinted's
     * minimum and the remaining steps are scaled from the actually-sent price.
     */
    private Boolean autoRaiseOfferToVintedMinimum;

    /**
     * Kept under the existing API/DB field name for backward compatibility.
     * Semantically this is now the global ceiling for every automatic offer
     * and accepted seller counteroffer in adaptive mode.
     */
    private BigDecimal maxAutomaticOffer;

    @NotNull
    private Integer dailyNegotiationBudget;

    @Valid
    @NotNull
    private List<CreateNegotiationStepRequest> negotiationSteps;

    @AssertTrue(
            message = "Negotiation step offer prices must be strictly increasing when adaptive pricing is enabled."
    )
    public boolean isNegotiationOfferLadderIncreasing() {
        if (!Boolean.TRUE.equals(autoRaiseOfferToVintedMinimum)) {
            return true;
        }

        if (negotiationSteps == null || negotiationSteps.size() < 2) {
            return true;
        }

        BigDecimal previous = null;

        for (CreateNegotiationStepRequest step : negotiationSteps) {
            if (step == null || step.getOfferPrice() == null) {
                return true;
            }

            BigDecimal current = step.getOfferPrice();

            if (previous != null && current.compareTo(previous) <= 0) {
                return false;
            }

            previous = current;
        }

        return true;
    }

    @AssertTrue(
            message = "Global negotiation cap cannot be lower than the first configured offer."
    )
    public boolean isGlobalNegotiationCapCompatibleWithFirstStep() {
        if (!Boolean.TRUE.equals(autoRaiseOfferToVintedMinimum)) {
            return true;
        }

        if (maxAutomaticOffer == null
                || negotiationSteps == null
                || negotiationSteps.isEmpty()
                || negotiationSteps.get(0) == null
                || negotiationSteps.get(0).getOfferPrice() == null) {
            return true;
        }

        return maxAutomaticOffer.compareTo(
                negotiationSteps.get(0).getOfferPrice()
        ) >= 0;
    }
}
