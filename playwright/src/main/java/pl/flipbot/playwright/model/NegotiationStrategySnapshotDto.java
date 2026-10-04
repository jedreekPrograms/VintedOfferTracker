package pl.flipbot.playwright.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class NegotiationStrategySnapshotDto {

    private Integer version;

    private Boolean autoRaiseOfferToVintedMinimum;

    private BigDecimal maxAutomaticOffer;

    private List<NegotiationStepDto> negotiationSteps = new ArrayList<>();
}
