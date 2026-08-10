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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.flipbot.bot.Bot;

import java.math.BigDecimal;
import java.time.LocalDateTime;
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Listing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            unique = true,
            nullable = false
    )
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

    @ManyToOne
    @JoinColumn(
            name = "bot_id",
            nullable = false
    )
    private Bot bot;



}