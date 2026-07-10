package pl.flipbot.listing;

import jakarta.persistence.*;
import lombok.*;
import pl.flipbot.bot.Bot;

import java.math.BigDecimal;

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

    @Column(unique = true)
    private String listingId;

    private String title;

    private Integer currentStep;

    private BigDecimal originalPrice;

    private BigDecimal currentPrice;

    @Enumerated(EnumType.STRING)
    private ListingStatus status;

    @ManyToOne
    @JoinColumn(name = "bot_id")
    private Bot bot;
}
