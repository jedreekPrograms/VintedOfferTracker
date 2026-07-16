package pl.flipbot.playwright.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class NegotiationStepDto {

    private Integer stepNumber;

    private BigDecimal offerPrice;

    private BigDecimal maxAcceptedCounterOffer;

    private String message;

}