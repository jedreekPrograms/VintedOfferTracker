package pl.flipbot.negotiation;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SellerCounterOfferRule {

    @Column(name = "minimum_discount_percent", nullable = false, precision = 7, scale = 3)
    private BigDecimal minimumDiscountPercent;

    @Enumerated(EnumType.STRING)
    @Column(name = "reaction_action", nullable = false, length = 40)
    private NegotiationReactionAction action;

    @Column(name = "wait_hours")
    private Integer waitHours;
}
