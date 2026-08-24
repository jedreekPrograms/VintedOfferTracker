package pl.flipbot.probe;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.flipbot.bot.Bot;
import pl.flipbot.listing.Listing;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "price_probe",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_price_probe_bot_listing",
                columnNames = {"probe_bot_id", "source_listing_id"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceProbe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "probe_bot_id", nullable = false)
    private Bot probeBot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_listing_id", nullable = false)
    private Listing sourceListing;

    @Column(name = "reference_offer_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal referenceOfferPrice;

    @Column(name = "probe_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal probePrice;

    @Column(nullable = false, length = 1000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PriceProbeStatus status;

    @Column(name = "claimed_at", nullable = false)
    private LocalDateTime claimedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;
}
