package pl.flipbot.negotiation;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.flipbot.bot.configuration.BotConfiguration;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NegotiationStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer stepNumber;

    private BigDecimal offerPrice;

    private BigDecimal maxAcceptedCounterOffer;

    @Column(length = 1000)
    private String message;

    /*
     * Reaction to a formal Vinted rejection of THIS step. The action controls
     * when the next configured step may be sent.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    @Builder.Default
    private NegotiationReactionAction rejectionAction =
            NegotiationReactionAction.NEXT_STEP_NOW;

    @Column
    private Integer rejectionWaitHours;

    /*
     * Fallback reaction for a seller counteroffer that is still above our
     * accepted-counteroffer threshold and does not match any discount rule.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    @Builder.Default
    private NegotiationReactionAction counterOfferDefaultAction =
            NegotiationReactionAction.WAIT_BEFORE_NEXT_STEP;

    @Column
    @Builder.Default
    private Integer counterOfferDefaultWaitHours = 6;

    /*
     * Rules are thresholds relative to the ORIGINAL listing price, never to
     * the current adaptive offer. Example for an original 2000 PLN listing:
     * a 1700 PLN seller counteroffer means a 15% discount.
     *
     * When several thresholds match, Playwright chooses the highest one.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "negotiation_step_counter_offer_rule",
            joinColumns = @JoinColumn(name = "negotiation_step_id")
    )
    @OrderColumn(name = "rule_index")
    @Builder.Default
    private List<SellerCounterOfferRule> counterOfferRules = new ArrayList<>();

    @ManyToOne
    @JoinColumn(name = "configuration_id")
    private BotConfiguration configuration;
}
