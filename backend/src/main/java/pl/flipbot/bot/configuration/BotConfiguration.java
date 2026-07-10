package pl.flipbot.bot.configuration;

import jakarta.persistence.*;
import lombok.*;
import pl.flipbot.bot.Bot;
import pl.flipbot.marketplace.Marketplace;

import java.math.BigDecimal;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BotConfiguration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private Marketplace marketplace;

    private String category;

    private String subCategory;

    private String brand;

    private String model;

    private BigDecimal minPrice;

    private BigDecimal maxPrice;

    private BigDecimal firstOffer;

    private BigDecimal maxOffer;

    private BigDecimal negotiationStep;

    private Integer maxNegotiationAttempts;

    @OneToOne
    @JoinColumn(name = "bod_id")
    private Bot bot;
}
