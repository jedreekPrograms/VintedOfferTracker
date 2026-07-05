package pl.flipbot.listing;

import jakarta.persistence.*;
import lombok.*;
import pl.flipbot.bot.Bot;

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

    private String title;

    private Double originalPrice;

    private Double currentPrice;

    @Enumerated(EnumType.STRING)
    private ListingStatus status;

    @ManyToOne
    private Bot bot;
}
