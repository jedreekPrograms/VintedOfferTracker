package pl.flipbot.marketstats;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    @Query(value = """
            select count(*)
            from market_listing_observation observation
            where observation.model_id = :modelId
              and observation.published_at is not null
              and observation.published_at >= :fromInclusive
              and observation.published_at < :toExclusive
            """, nativeQuery = true)
    long countPublishedListingsBetween(
            @Param("modelId") Long modelId,
            @Param("fromInclusive") LocalDateTime fromInclusive,
            @Param("toExclusive") LocalDateTime toExclusive
    );

    @Query(value = """
            select observation.marketplace_listing_id
            from market_listing_observation observation
            where observation.model_id = :modelId
              and observation.published_at is null
            order by observation.last_seen_at desc
            """, nativeQuery = true)
    List<String> findListingIdsMissingPublishedAt(
            @Param("modelId") Long modelId
    );

    @Modifying
    @Query(value = """
            update market_listing_observation
            set published_at = :publishedAt
            where model_id = :modelId
              and marketplace_listing_id = :listingId
            """, nativeQuery = true)
    int updatePublishedAt(
            @Param("modelId") Long modelId,
            @Param("listingId") String listingId,
            @Param("publishedAt") LocalDateTime publishedAt
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
