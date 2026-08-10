package pl.flipbot.negotiation.quota;

import jakarta.persistence.*;
import lombok.*;
import pl.flipbot.bot.Bot;

import java.time.LocalDate;

@Entity
@Table(
        name = "daily_offer_quota",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_daily_offer_quota_bot_date",
                        columnNames = {
                                "bot_id",
                                "usage_date"
                        }
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyOfferQuota {

    @Id
    @GeneratedValue(
            strategy = GenerationType.IDENTITY
    )
    private Long id;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "bot_id",
            nullable = false
    )
    private Bot bot;

    @Column(
            name = "usage_date",
            nullable = false
    )
    private LocalDate usageDate;

    @Column(
            name = "used_count",
            nullable = false
    )
    private int usedCount;
}