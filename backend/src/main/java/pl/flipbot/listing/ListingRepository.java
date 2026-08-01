package pl.flipbot.listing;

import org.springframework.data.jpa.repository.JpaRepository;

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

}