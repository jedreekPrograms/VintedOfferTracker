package pl.flipbot.listing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "listing",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_listing_bot_marketplace",
                columnNames = {"bot_id", "listing_id"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Listing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String listingId;

    @Column(nullable = false)
    private String title;

    @Column(
            length = 1000,
            nullable = false
    )
    private String url;

    @Column(nullable = false)
    private BigDecimal originalPrice;

    @Column(nullable = false)
    private BigDecimal currentPrice;

    @Column(nullable = false)
    private Integer currentStep;

    @Column(nullable = false)
    private Boolean awaitingSellerResponse;

    @Column(length = 255)
    private String conversationId;

    @Column(length = 1000)
    private String conversationUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ListingStatus status;

    @Column(name = "decision_at")
    private LocalDateTime decisionAt;

    @Column(name = "history_hidden", nullable = false)
    private boolean historyHidden;

    @Column(name = "current_step_started_at")
    private LocalDateTime currentStepStartedAt;

    @Column(name = "seller_activity_at")
    private LocalDateTime sellerActivityAt;

    @Column(name = "read_detected_at")
    private LocalDateTime readDetectedAt;

    /*
     * Formal response timing is deliberately persisted. Vinted may show the
     * same "rejected" state or the same counteroffer on every poll. Without a
     * stable first-detection timestamp a configurable 6h/12h/24h timer would
     * restart forever.
     *
     * Fingerprints are scoped to the current step and are reset whenever a new
     * step starts. A changed counteroffer price creates a new fingerprint and
     * therefore a new timer, which is exactly what we want for a fresh seller
     * concession.
     */
    @Column(name = "formal_response_fingerprint", length = 255)
    private String formalResponseFingerprint;

    @Column(name = "formal_response_detected_at")
    private LocalDateTime formalResponseDetectedAt;

    @ManyToOne
    @JoinColumn(
            name = "bot_id",
            nullable = false
    )
    private Bot bot;
}
