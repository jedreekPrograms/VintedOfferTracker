package pl.flipbot.negotiation;

import jakarta.persistence.*;
import lombok.*;
import pl.flipbot.bot.configuration.BotConfiguration;

import java.math.BigDecimal;

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

    @ManyToOne
    @JoinColumn(name = "configuration_id")
    private BotConfiguration configuration;


}
