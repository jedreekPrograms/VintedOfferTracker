package pl.flipbot.listing;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pl.flipbot.bot.Bot;
import pl.flipbot.bot.BotRepository;
import pl.flipbot.listing.dto.CreateListingRequest;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class ListingClaimService {

    private static final ZoneId DISCOVERY_ZONE =
            ZoneId.of("Europe/Warsaw");

    private final ListingRepository listingRepository;

    private final BotRepository botRepository;

    @Transactional(
            propagation = Propagation.REQUIRES_NEW
    )
    public Listing claimListing(
            Long botId,
            CreateListingRequest request
    ) {

        Bot bot = botRepository.getReferenceById(botId);

        Listing listing = Listing.builder()
                .listingId(request.getListingId())
                .title(request.getTitle())
                .url(request.getUrl())
                .originalPrice(request.getOriginalPrice())
                .currentPrice(request.getOriginalPrice())
                .currentStep(0)
                .awaitingSellerResponse(false)
                .status(ListingStatus.DISCOVERED)
                .lastFreshDiscoveryAt(LocalDateTime.now(DISCOVERY_ZONE))
                .bot(bot)
                .build();

        return listingRepository.saveAndFlush(listing);

    }

}
