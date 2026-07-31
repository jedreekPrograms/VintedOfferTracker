package pl.flipbot.listing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.flipbot.bot.Bot;
import pl.flipbot.bot.BotRepository;
import pl.flipbot.exception.BotNotFoundException;
import pl.flipbot.listing.dto.CreateListingRequest;
import pl.flipbot.listing.dto.DiscoverListingsRequest;
import pl.flipbot.listing.dto.ListingResponse;
import pl.flipbot.listing.dto.UpdateListingRequest;
import pl.flipbot.mapper.ListingMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ListingService {

    private final ListingRepository listingRepository;

    private final BotRepository botRepository;

    private final ListingMapper listingMapper;

    private final ListingClaimService listingClaimService;

    public List<ListingResponse> getNegotiatingListings(
            Long botId
    ) {

        return listingRepository.findByBotIdAndStatus(
                        botId,
                        ListingStatus.NEGOTIATING
                )
                .stream()
                .map(listingMapper::map)
                .toList();

    }

    public List<ListingResponse> discoverListings(
            Long botId,
            DiscoverListingsRequest request
    ) {

        if (!botRepository.existsById(botId)) {
            throw new BotNotFoundException(botId);
        }

        Map<String, CreateListingRequest> uniqueRequests =
                removeDuplicatedRequests(
                        request.getListings()
                );

        if (uniqueRequests.isEmpty()) {
            return List.of();
        }

        Set<String> existingListingIds =
                findExistingListingIds(
                        uniqueRequests.keySet()
                );

        List<ListingResponse> claimedListings =
                new ArrayList<>();

        for (CreateListingRequest listingRequest
                : uniqueRequests.values()) {

            if (existingListingIds.contains(
                    listingRequest.getListingId()
            )) {

                continue;

            }

            try {

                Listing claimedListing =
                        listingClaimService.claimListing(
                                botId,
                                listingRequest
                        );

                claimedListings.add(
                        listingMapper.map(claimedListing)
                );

            } catch (DataIntegrityViolationException exception) {

                log.debug(
                        "Listing {} was claimed concurrently by another bot",
                        listingRequest.getListingId()
                );

            }

        }

        log.info(
                "Bot {} discovered {} listings and claimed {} new listings",
                botId,
                uniqueRequests.size(),
                claimedListings.size()
        );

        return claimedListings;

    }

    @Transactional
    public ListingResponse createListing(
            Long botId,
            CreateListingRequest request
    ) {

        Bot bot = botRepository.findById(botId)
                .orElseThrow(
                        () -> new BotNotFoundException(botId)
                );

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

        Listing savedListing =
                listingRepository.save(listing);

        return listingMapper.map(savedListing);

    }

    @Transactional
    public ListingResponse updateListing(
            Long listingId,
            UpdateListingRequest request
    ) {

        Listing listing =
                listingRepository.findById(listingId)
                        .orElseThrow();

        listing.setCurrentPrice(
                request.getCurrentPrice()
        );

        listing.setCurrentStep(
                request.getCurrentStep()
        );

        listing.setStatus(
                request.getStatus()
        );

        return listingMapper.map(listing);

    }

    private Map<String, CreateListingRequest>
    removeDuplicatedRequests(
            List<CreateListingRequest> requests
    ) {

        Map<String, CreateListingRequest> uniqueRequests =
                new LinkedHashMap<>();

        for (CreateListingRequest request : requests) {

            uniqueRequests.putIfAbsent(
                    request.getListingId(),
                    request
            );

        }

        return uniqueRequests;

    }

    private Set<String> findExistingListingIds(
            Set<String> listingIds
    ) {

        List<Listing> existingListings =
                listingRepository.findAllByListingIdIn(
                        listingIds
                );

        Set<String> existingListingIds =
                new HashSet<>();

        for (Listing listing : existingListings) {

            existingListingIds.add(
                    listing.getListingId()
            );

        }

        return existingListingIds;

    }

}