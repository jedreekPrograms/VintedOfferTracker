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
    findAllByModel_IdAndTrackingGenerationAndMarketplaceListingIdIn(
            Long modelId,
            Integer trackingGeneration,
            Collection<String> marketplaceListingIds
    );

    List<MarketListingObservation>
    findAllByModel_IdAndTrackingGenerationAndPublishedAtIsNotNull(
            Long modelId,
            Integer trackingGeneration
    );

    long countByModel_IdAndTrackingGenerationAndBaselineFalseAndFirstSeenAtAfter(
            Long modelId,
            Integer trackingGeneration,
            LocalDateTime firstSeenAfter
    );

    /**
     * All calendar publication windows in one grouped PostgreSQL query.
     * Joining the scan-state generation is essential: old observer generations
     * must never be counted as part of the active model's publication totals.
     *
     * Columns: model ID, today, current week, previous full week, since baseline.
     * All ranges preserve the original inclusive start / exclusive end rule.
     */
    @Query(value = """
            select scan.model_id,
                   count(*) filter (
                       where observation.published_at >= :todayStart
                         and observation.published_at < :now
                   ),
                   count(*) filter (
                       where observation.published_at >= :currentWeekStart
                         and observation.published_at < :now
                   ),
                   count(*) filter (
                       where observation.published_at >= :previousWeekStart
                         and observation.published_at < :currentWeekStart
                   ),
                   count(*) filter (
                       where observation.published_at >= scan.baseline_complete_at
                         and observation.published_at < :now
                   )
            from market_model_scan_state scan
            join market_listing_observation observation
              on observation.model_id = scan.model_id
             and observation.tracking_generation =
                   case when scan.tracking_generation is null
                              or scan.tracking_generation < 1
                        then 1 else scan.tracking_generation end
            where scan.model_id in (:modelIds)
              and scan.baseline_complete_at is not null
              and observation.published_at is not null
            group by scan.model_id
            """, nativeQuery = true)
    List<Object[]> countPublishedListingWindows(
            @Param("modelIds") Collection<Long> modelIds,
            @Param("todayStart") LocalDateTime todayStart,
            @Param("currentWeekStart") LocalDateTime currentWeekStart,
            @Param("previousWeekStart") LocalDateTime previousWeekStart,
            @Param("now") LocalDateTime now
    );

    @Query(value = """
            select observation.marketplace_listing_id
            from market_listing_observation observation
            where observation.model_id = :modelId
              and observation.tracking_generation = :trackingGeneration
              and observation.published_at is null
            order by observation.last_seen_at desc
            """, nativeQuery = true)
    List<String> findListingIdsMissingPublishedAt(
            @Param("modelId") Long modelId,
            @Param("trackingGeneration") Integer trackingGeneration
    );

    @Modifying
    @Query(value = """
            update market_listing_observation
            set published_at = :publishedAt
            where model_id = :modelId
              and tracking_generation = :trackingGeneration
              and marketplace_listing_id = :listingId
            """, nativeQuery = true)
    int updatePublishedAt(
            @Param("modelId") Long modelId,
            @Param("trackingGeneration") Integer trackingGeneration,
            @Param("listingId") String listingId,
            @Param("publishedAt") LocalDateTime publishedAt
    );

    long countByModel_IdAndTrackingGenerationAndBaselineTrue(
            Long modelId,
            Integer trackingGeneration
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
              and observation.trackingGeneration = :trackingGeneration
              and observation.lastSeenAt >= :cutoff
            order by observation.lastSeenAt desc
            """)
    List<String> findKnownListingIds(
            @Param("modelId") Long modelId,
            @Param("trackingGeneration") Integer trackingGeneration,
            @Param("cutoff") LocalDateTime cutoff
    );
}
