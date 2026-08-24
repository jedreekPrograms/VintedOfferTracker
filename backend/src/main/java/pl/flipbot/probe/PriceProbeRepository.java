package pl.flipbot.probe;

import org.springframework.data.jpa.repository.JpaRepository;

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
}
