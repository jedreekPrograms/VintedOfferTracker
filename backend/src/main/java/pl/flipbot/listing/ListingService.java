package pl.flipbot.listing;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.flipbot.bot.Bot;
import pl.flipbot.bot.BotRepository;
import pl.flipbot.exception.BotNotFoundException;
import pl.flipbot.listing.dto.CreateListingRequest;
import pl.flipbot.listing.dto.ListingResponse;
import pl.flipbot.listing.dto.UpdateListingRequest;
import pl.flipbot.mapper.ListingMapper;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListingService {

    private final ListingRepository listingRepository;
    private final BotRepository botRepository;
    private final ListingMapper listingMapper;

    public List<ListingResponse> getNegotiatingListings(Long botId) {

        return listingRepository.findByBotIdAndStatus(
                        botId,
                        ListingStatus.NEGOTIATING
                )
                .stream()
                .map(listingMapper::map)
                .toList();

    }

    @Transactional
    public ListingResponse createListing(Long botId,
                                         CreateListingRequest request) {

        Bot bot = botRepository.findById(botId)
                .orElseThrow(() -> new BotNotFoundException(botId));

        Listing listing = Listing.builder()
                .listingId(request.getListingId())
                .title(request.getTitle())
                .url(request.getUrl())
                .originalPrice(request.getOriginalPrice())
                .currentPrice(request.getOriginalPrice())
                .currentStep(1)
                .awaitingSellerResponse(false)
                .status(ListingStatus.NEGOTIATING)
                .bot(bot)
                .build();

        Listing savedListing = listingRepository.save(listing);

        return listingMapper.map(savedListing);

    }

    @Transactional
    public ListingResponse updateListing(Long listingId,
                                         UpdateListingRequest request) {

        Listing listing = listingRepository.findById(listingId)
                .orElseThrow();

        listing.setCurrentPrice(request.getCurrentPrice());
        listing.setCurrentStep(request.getCurrentStep());
        listing.setStatus(request.getStatus());

        return listingMapper.map(listing);

    }

}