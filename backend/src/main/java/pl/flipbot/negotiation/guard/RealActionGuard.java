package pl.flipbot.negotiation.guard;

import jakarta.persistence.*;
import lombok.*;
import pl.flipbot.listing.Listing;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "real_action_guard",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_real_action_guard_listing", columnNames = "listing_id"),
                @UniqueConstraint(name = "uk_real_action_guard_request", columnNames = "request_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RealActionGuard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listing_id", nullable = false)
    private Listing listing;

    @Column(name = "request_id", nullable = false, unique = true)
    private UUID requestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 32)
    private RealActionType actionType;

    @Column(name = "step_number", nullable = false)
    private Integer stepNumber;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
