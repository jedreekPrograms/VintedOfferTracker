package pl.flipbot.negotiation.guard;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RealActionGuardRepository
        extends JpaRepository<RealActionGuard, Long> {

    Optional<RealActionGuard> findByListing_Id(Long listingId);

    Optional<RealActionGuard> findByRequestId(UUID requestId);

    void deleteByListing_Id(Long listingId);
}
