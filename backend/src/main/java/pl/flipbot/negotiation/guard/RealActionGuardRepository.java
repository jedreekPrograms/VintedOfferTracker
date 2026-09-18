package pl.flipbot.negotiation.guard;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RealActionGuardRepository
        extends JpaRepository<RealActionGuard, Long> {

    Optional<RealActionGuard> findByListing_Id(Long listingId);

    Optional<RealActionGuard> findByRequestId(UUID requestId);

    void deleteByListing_Id(Long listingId);

    @Query("""
            select count(guard)
            from RealActionGuard guard
            where guard.listing.bot.id = :botId
              and (
                    guard.listing.currentStep is null
                    or guard.listing.currentStep < guard.stepNumber
              )
            """)
    long countUnresolvedByBotId(@Param("botId") Long botId);
}
