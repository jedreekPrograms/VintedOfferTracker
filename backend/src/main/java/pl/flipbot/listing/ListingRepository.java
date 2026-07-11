package pl.flipbot.listing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ListingRepository extends JpaRepository<Listing, Long> {

    boolean existsByListingId(String listingId);

    Optional<Listing> findByListingId(String listingId);

    List<Listing> findByBotId(Long botId);

    List<Listing> findByBotIdAndStatus(Long botId, ListingStatus status);

}