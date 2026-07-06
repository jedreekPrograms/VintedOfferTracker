package pl.flipbot.bot.configuration;

import jakarta.persistence.*;
import lombok.*;
import pl.flipbot.bot.Bot;

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

    private String category;

    private String subCategory;

    private String brand;

    private String model;

    private Double minPrice;

    private Double maxPrice;

    private Double firstOffer;

    private Double maxOffer;

    private Double negotiationStep;

    private Integer maxNegotiationAttempts;

    @OneToOne
    @JoinColumn(name = "bod_id")
    private Bot bot;
}
