package pl.flipbot.marketstats;

import jakarta.persistence.*;
import lombok.*;
import pl.flipbot.dictionary.DictionaryModel;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "market_listing_observation",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_market_listing_observation_model_listing",
                columnNames = {"model_id", "marketplace_listing_id"}
        )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketListingObservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "model_id", nullable = false)
    private DictionaryModel model;

    @Column(name = "marketplace_listing_id", nullable = false, length = 255)
    private String marketplaceListingId;

    @Column(name = "first_seen_at", nullable = false)
    private LocalDateTime firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "first_seen_price", precision = 38, scale = 2)
    private BigDecimal firstSeenPrice;

    @Column(name = "latest_price", precision = 38, scale = 2)
    private BigDecimal latestPrice;

    @Column(name = "lowest_seen_price", precision = 38, scale = 2)
    private BigDecimal lowestSeenPrice;

    @Column(name = "highest_seen_price", precision = 38, scale = 2)
    private BigDecimal highestSeenPrice;

    @Column(nullable = false)
    private Boolean baseline;
}