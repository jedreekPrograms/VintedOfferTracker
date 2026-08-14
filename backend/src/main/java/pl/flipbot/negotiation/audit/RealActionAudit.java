package pl.flipbot.negotiation.audit;

import jakarta.persistence.*;
import lombok.*;
import pl.flipbot.negotiation.guard.RealActionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "real_action_audit",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_real_action_audit_request",
                        columnNames = "request_id"
                )
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RealActionAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false, unique = true)
    private UUID requestId;

    @Column(name = "bot_id", nullable = false)
    private Long botId;

    @Column(name = "backend_listing_id", nullable = false)
    private Long backendListingId;

    @Column(name = "marketplace_listing_id", nullable = false, length = 255)
    private String marketplaceListingId;

    @Column(name = "conversation_id", length = 255)
    private String conversationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 32)
    private RealActionType actionType;

    @Column(name = "step_number", nullable = false)
    private Integer stepNumber;

    @Column(name = "offer_price", nullable = false, precision = 38, scale = 2)
    private BigDecimal offerPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 32)
    private RealActionAuditOutcome outcome;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_status", nullable = false, length = 32)
    private RealActionMessageStatus messageStatus;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
