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

    private String listingId;

    private String title;

    @Column(length = 1000)
    private String url;

    private BigDecimal originalPrice;

    private BigDecimal currentPrice;

    private Integer currentStep;

    @Enumerated(EnumType.STRING)
    private ListingStatus status;

    @ManyToOne
    @JoinColumn(name = "bot_id")
    private Bot bot;
}
