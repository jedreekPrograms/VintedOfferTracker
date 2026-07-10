package pl.flipbot.listing;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ListingRepository extends JpaRepository<Listing, Long> {

    boolean existsByListingId(String listingId);

}
