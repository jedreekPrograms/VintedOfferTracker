package pl.flipbot.marketstats;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface MarketListingObservationRepository
        extends JpaRepository<MarketListingObservation, Long> {

    List<MarketListingObservation>
    findAllByModel_IdAndMarketplaceListingIdIn(
            Long modelId,
            Collection<String> marketplaceListingIds
    );

    long countByModel_IdAndBaselineFalseAndFirstSeenAtAfter(
            Long modelId,
            LocalDateTime firstSeenAfter
    );

    @Query("""
            select count(observation)
            from MarketListingObservation observation
            where observation.model.id = :modelId
              and observation.firstSeenAt >= :fromInclusive
              and observation.firstSeenAt < :toExclusive
            """)
    long countNewListingsBetween(
            @Param("modelId") Long modelId,
            @Param("fromInclusive") LocalDateTime fromInclusive,
            @Param("toExclusive") LocalDateTime toExclusive
    );

    long countByModel_IdAndBaselineTrue(
            Long modelId
    );

    long deleteByModel_Id(
            Long modelId
    );

    long deleteByLastSeenAtBefore(
            LocalDateTime cutoff
    );

    @Query("""
            select observation.marketplaceListingId
            from MarketListingObservation observation
            where observation.model.id = :modelId
              and observation.lastSeenAt >= :cutoff
            order by observation.lastSeenAt desc
            """)
    List<String> findKnownListingIds(
            @Param("modelId") Long modelId,
            @Param("cutoff") LocalDateTime cutoff
    );
}
