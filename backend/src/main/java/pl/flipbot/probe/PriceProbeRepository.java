package pl.flipbot.probe;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PriceProbeRepository
        extends JpaRepository<PriceProbe, Long> {

    boolean existsByProbeBot_IdAndSourceListing_Id(
            Long probeBotId,
            Long sourceListingId
    );

    long countBySourceListing_Id(
            Long sourceListingId
    );

    Optional<PriceProbe> findByIdAndProbeBot_Id(
            Long probeId,
            Long probeBotId
    );
}
