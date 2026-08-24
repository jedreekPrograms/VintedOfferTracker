package pl.flipbot.probe;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PriceProbeRepository extends JpaRepository<PriceProbe, Long> {

    boolean existsByProbeBot_IdAndSourceListing_Id(Long probeBotId, Long sourceListingId);

    @Query("""
            select count(probe)
            from PriceProbe probe
            where probe.sourceListing.id = :sourceListingId
              and probe.status in (pl.flipbot.probe.PriceProbeStatus.CLAIMED,
                                   pl.flipbot.probe.PriceProbeStatus.SENT,
                                   pl.flipbot.probe.PriceProbeStatus.UNKNOWN)
            """)
    long countReservedSlots(@Param("sourceListingId") Long sourceListingId);

    Optional<PriceProbe> findByIdAndProbeBot_Id(Long probeId, Long probeBotId);
}
