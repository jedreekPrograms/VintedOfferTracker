package pl.flipbot.listing;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.flipbot.bot.Bot;
import pl.flipbot.bot.BotRepository;
import pl.flipbot.exception.BotNotFoundException;
import pl.flipbot.listing.dto.CreateListingRequest;
import pl.flipbot.listing.dto.ListingResponse;
import pl.flipbot.mapper.ListingMapper;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListingService {

    private final ListingRepository listingRepository;
    private final BotRepository botRepository;
    private final ListingMapper listingMapper;

    public List<ListingResponse> getAllListings() {

        return listingRepository.findAll()
                .stream()
                .map(listingMapper::map)
                .toList();
    }

    @Transactional
    public ListingResponse createListing(Long botId, CreateListingRequest request) {

        Bot bot = botRepository.findById(botId).orElseThrow(() -> new BotNotFoundException(botId));

        Listing listing = Listing.builder()
                .listingId(request.getListingId())
                .title(request.getTitle())
                .url(request.getUrl())
                .originalPrice(request.getOriginalPrice())
                .currentPrice(request.getOriginalPrice())
                .currentStep(1)
                .status(ListingStatus.NEW)
                .bot(bot)
                .build();

        Listing savedListing = listingRepository.save(listing);

        return listingMapper.map(savedListing);
    }
}
