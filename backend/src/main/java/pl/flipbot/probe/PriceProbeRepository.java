package pl.flipbot.probe;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PriceProbeRepository extends JpaRepository<PriceProbe, Long> {

    boolean existsByProbeBot_IdAndSourceListing_Id(Long probeBotId, Long sourceListingId);

    long countBySourceListing_IdAndStatusIn(
            Long sourceListingId,
            Collection<PriceProbeStatus> statuses
    );

    default long countReservedSlots(Long sourceListingId) {
        return countBySourceListing_IdAndStatusIn(
                sourceListingId,
                List.of(
                        PriceProbeStatus.CLAIMED,
                        PriceProbeStatus.SENT,
                        PriceProbeStatus.UNKNOWN
                )
        );
    }

    Optional<PriceProbe> findByIdAndProbeBot_Id(Long probeId, Long probeBotId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE PriceProbe probe
            SET probe.status = :unknownStatus,
                probe.completedAt = :completedAt,
                probe.failureReason = :failureReason
            WHERE probe.status = :claimedStatus
              AND probe.claimedAt <= :claimedBefore
            """)
    int transitionStaleClaimsToUnknown(
            @Param("claimedStatus") PriceProbeStatus claimedStatus,
            @Param("unknownStatus") PriceProbeStatus unknownStatus,
            @Param("claimedBefore") LocalDateTime claimedBefore,
            @Param("completedAt") LocalDateTime completedAt,
            @Param("failureReason") String failureReason
    );
}
