package pl.flipbot.bot.configuration;

import jakarta.persistence.*;
import lombok.*;
import pl.flipbot.bot.Bot;
import pl.flipbot.marketplace.Marketplace;
import pl.flipbot.negotiation.NegotiationStep;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

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

    @OneToOne
    @JoinColumn(name = "bod_id")
    private Bot bot;

    @OneToMany(mappedBy = "configuration", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<NegotiationStep> negotiationSteps = new ArrayList<>();
}
