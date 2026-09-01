package pl.flipbot.negotiation.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.flipbot.negotiation.guard.RealActionType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RealActionAuditRepository
        extends JpaRepository<RealActionAudit, Long> {

    Optional<RealActionAudit> findByRequestId(UUID requestId);

    List<RealActionAudit> findByBotIdOrderByCreatedAtDesc(Long botId);

    boolean existsByBackendListingIdAndActionTypeAndOutcome(
            Long backendListingId,
            RealActionType actionType,
            RealActionAuditOutcome outcome
    );

    List<RealActionAudit>
    findAllByActionTypeAndOutcomeAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(
            RealActionType actionType,
            RealActionAuditOutcome outcome,
            LocalDateTime createdAt
    );
}
