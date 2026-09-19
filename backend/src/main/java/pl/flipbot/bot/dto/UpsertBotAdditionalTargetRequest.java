package pl.flipbot.bot.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import pl.flipbot.bot.configuration.TargetMode;
import pl.flipbot.negotiation.dto.CreateNegotiationStepRequest;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class UpsertBotAdditionalTargetRequest {

    @NotEmpty
    private List<String> categoryPath;

    @NotBlank
    private String brand;

    @NotNull
    private TargetMode targetMode;

    private String model;

    private String searchQuery;

    @NotNull
    private BigDecimal minPrice;

    @NotNull
    private BigDecimal maxPrice;

    private Boolean autoRaiseOfferToVintedMinimum;

    private BigDecimal maxAutomaticOffer;

    @Valid
    @NotEmpty
    @Size(max = 25)
    private List<CreateNegotiationStepRequest> negotiationSteps;

    @AssertTrue(message = "Cena minimalna nie może być wyższa od ceny maksymalnej.")
    public boolean isPriceRangeValid() {
        return minPrice == null
                || maxPrice == null
                || minPrice.compareTo(maxPrice) <= 0;
    }

    @AssertTrue(message = "Dla trybu VINTED_MODEL model jest wymagany, a dla SEARCH_QUERY wymagane jest wyszukiwanie tekstowe.")
    public boolean isTargetDefinitionValid() {
        if (targetMode == null) {
            return true;
        }
        return switch (targetMode) {
            case VINTED_MODEL -> model != null && !model.isBlank();
            case SEARCH_QUERY -> searchQuery != null && !searchQuery.isBlank();
        };
    }

    @AssertTrue(message = "Kroki ofert muszą rosnąć, gdy włączone jest automatyczne podnoszenie ceny.")
    public boolean isAdaptiveOfferLadderIncreasing() {
        if (!Boolean.TRUE.equals(autoRaiseOfferToVintedMinimum)
                || negotiationSteps == null
                || negotiationSteps.size() < 2) {
            return true;
        }

        BigDecimal previous = null;
        for (CreateNegotiationStepRequest step : negotiationSteps) {
            if (step == null || step.getOfferPrice() == null) {
                return true;
            }
            if (previous != null && step.getOfferPrice().compareTo(previous) <= 0) {
                return false;
            }
            previous = step.getOfferPrice();
        }
        return true;
    }

    @AssertTrue(message = "Maksymalna automatyczna oferta nie może być niższa niż pierwszy krok.")
    public boolean isAutomaticCapCompatible() {
        if (!Boolean.TRUE.equals(autoRaiseOfferToVintedMinimum)
                || maxAutomaticOffer == null
                || negotiationSteps == null
                || negotiationSteps.isEmpty()
                || negotiationSteps.getFirst() == null
                || negotiationSteps.getFirst().getOfferPrice() == null) {
            return true;
        }
        return maxAutomaticOffer.compareTo(
                negotiationSteps.getFirst().getOfferPrice()
        ) >= 0;
    }
}
