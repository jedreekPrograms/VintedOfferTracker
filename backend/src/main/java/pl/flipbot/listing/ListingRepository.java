package pl.flipbot.listing;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ListingRepository
        extends JpaRepository<Listing, Long> {

    boolean existsByBotIdAndListingId(
            Long botId,
            String listingId
    );

    Optional<Listing> findByBotIdAndListingId(
            Long botId,
            String listingId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select listing
            from Listing listing
            where listing.bot.id = :botId
              and listing.listingId = :marketplaceListingId
            """)
    Optional<Listing> findByBotIdAndListingIdForUpdate(
            @Param("botId") Long botId,
            @Param("marketplaceListingId") String marketplaceListingId
    );

    Optional<Listing> findByIdAndBotId(
            Long listingId,
            Long botId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select listing
            from Listing listing
            where listing.id = :listingId
              and listing.bot.id = :botId
            """)
    Optional<Listing> findByIdAndBotIdForUpdate(
            @Param("listingId") Long listingId,
            @Param("botId") Long botId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select listing
            from Listing listing
            where listing.id = :listingId
            """)
    Optional<Listing> findByIdForUpdate(
            @Param("listingId") Long listingId
    );

    @Query("""
            select listing
            from Listing listing
            where listing.status in :statuses
            order by listing.id asc
            """)
    List<Listing> findByStatusInOrderByIdAsc(
            @Param("statuses") Collection<ListingStatus> statuses
    );

    List<Listing> findByBotId(
            Long botId
    );

    List<Listing> findByBotIdAndStatusOrderByIdAsc(
            Long botId,
            ListingStatus status
    );

    List<Listing> findByBotIdAndStatusAndAdditionalTargetIsNullOrderByIdAsc(
            Long botId,
            ListingStatus status
    );

    List<Listing> findByBotIdAndStatusAndAdditionalTargetIdOrderByIdAsc(
            Long botId,
            ListingStatus status,
            Long additionalTargetId
    );

    @Query("""
            select distinct listing.additionalTarget.id
            from Listing listing
            where listing.bot.id = :botId
              and listing.additionalTarget is not null
              and listing.status in :statuses
            """)
    List<Long> findDistinctAdditionalTargetIdsByBotIdAndStatusIn(
            @Param("botId") Long botId,
            @Param("statuses") Collection<ListingStatus> statuses
    );

    long countByBotIdAndStatus(
            Long botId,
            ListingStatus status
    );

    List<Listing> findAllByBotIdAndListingIdIn(
            Long botId,
            Collection<String> listingIds
    );

    @Query("""
            select distinct listing.bot.id
            from Listing listing
            where listing.status = :status
              and listing.bot.id in :botIds
            """)
    List<Long> findDistinctBotIdsByStatusAndBotIdIn(
            @Param("status") ListingStatus status,
            @Param("botIds") Collection<Long> botIds
    );

    interface StatusCount {
        ListingStatus getStatus();
        long getTotal();
    }

    interface PurchaseAmounts {
        BigDecimal getOriginalPrice();
        BigDecimal getCurrentPrice();
    }

    interface HistoryRow extends PurchaseAmounts {
        Long getId();
        String getListingId();
        String getTitle();
        String getUrl();
        Integer getCurrentStep();
        ListingStatus getStatus();
        LocalDateTime getDecisionAt();
        Long getBotId();
        String getBotName();
        Long getAdditionalTargetId();
        String getProductTargetLabel();
    }

    @Query("select listing.status as status, count(listing) as total from Listing listing group by listing.status")
    List<StatusCount> countByListingStatus();

    @Query("""
            select listing.status as status, count(listing) as total from Listing listing
            where listing.historyHidden = false and listing.status in :statuses
            and (:allTime = true or listing.decisionAt >= :from)
            group by listing.status
            """)
    List<StatusCount> countVisibleHistory(@Param("statuses") Collection<ListingStatus> statuses,
            @Param("allTime") boolean allTime, @Param("from") LocalDateTime from);

    @Query("""
            select listing.originalPrice as originalPrice, listing.currentPrice as currentPrice
            from Listing listing
            where listing.historyHidden = false and listing.status = :status
            and (:allTime = true or listing.decisionAt >= :from)
            """)
    List<PurchaseAmounts> findPurchaseAmounts(@Param("status") ListingStatus status,
            @Param("allTime") boolean allTime, @Param("from") LocalDateTime from);

    @Query("""
            select listing.id as id, listing.listingId as listingId, listing.title as title,
                   listing.url as url, listing.originalPrice as originalPrice,
                   listing.currentPrice as currentPrice, listing.currentStep as currentStep,
                   listing.status as status, listing.decisionAt as decisionAt,
                   bot.id as botId, bot.name as botName, target.id as additionalTargetId,
                   listing.productTargetLabel as productTargetLabel
            from Listing listing join listing.bot bot left join listing.additionalTarget target
            where listing.historyHidden = false and listing.status in :statuses
            order by listing.decisionAt desc nulls last, listing.id asc
            """)
    List<HistoryRow> findVisibleHistory(@Param("statuses") Collection<ListingStatus> statuses);

}
