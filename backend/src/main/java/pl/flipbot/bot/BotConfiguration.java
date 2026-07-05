package pl.flipbot.bot;

import jakarta.persistence.*;
import lombok.*;

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
