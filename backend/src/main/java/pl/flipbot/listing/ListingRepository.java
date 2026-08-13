package pl.flipbot.listing;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ListingRepository
        extends JpaRepository<Listing, Long> {

    boolean existsByListingId(
            String listingId
    );

    Optional<Listing> findByListingId(
            String listingId
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

    List<Listing> findByBotId(
            Long botId
    );

    List<Listing> findByBotIdAndStatusOrderByIdAsc(
            Long botId,
            ListingStatus status
    );

    List<Listing> findAllByListingIdIn(
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

}
