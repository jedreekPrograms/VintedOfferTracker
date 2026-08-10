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

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.time.LocalDateTime;
@Slf4j
@Service
@RequiredArgsConstructor
public class ListingService {

    private static final String UNIQUE_VIOLATION_SQL_STATE =
            "23505";

    private final ListingRepository listingRepository;

    private final BotRepository botRepository;

    private final ListingMapper listingMapper;

    private final ListingClaimService listingClaimService;

    public List<ListingResponse> getDiscoveredListings(
            Long botId
    ) {

        return getListingsByStatus(
                botId,
                ListingStatus.DISCOVERED
        );

    }

    public List<ListingResponse> getNegotiatingListings(
            Long botId
    ) {

        return getListingsByStatus(
                botId,
                ListingStatus.NEGOTIATING
        );

    }

    public List<ListingResponse> getActionRequiredListings(
            Long botId
    ) {

        return getListingsByStatus(
                botId,
                ListingStatus.ACTION_REQUIRED
        );

    }

    public List<ListingResponse> getPurchasedListings(
            Long botId
    ) {

        return getListingsByStatus(
                botId,
                ListingStatus.PURCHASED
        );
    }

    public List<ListingResponse> getSkippedByUserListings(
            Long botId
    ) {

        return getListingsByStatus(
                botId,
                ListingStatus.SKIPPED_BY_USER
        );
    }

    @Transactional
    public ListingResponse markAsPurchased(
            Long botId,
            Long listingId
    ) {

        Listing listing =
                findActionRequiredListing(
                        botId,
                        listingId
                );

        listing.setStatus(
                ListingStatus.PURCHASED
        );

        listing.setAwaitingSellerResponse(
                false
        );

        listing.setDecisionAt(
                LocalDateTime.now()
        );

        log.info(
                "Listing {} for bot {} was manually marked as PURCHASED",
                listingId,
                botId
        );

        return listingMapper.map(
                listing
        );
    }

    @Transactional
    public ListingResponse skipByUser(
            Long botId,
            Long listingId
    ) {

        Listing listing =
                findActionRequiredListing(
                        botId,
                        listingId
                );

        listing.setStatus(
                ListingStatus.SKIPPED_BY_USER
        );

        listing.setAwaitingSellerResponse(
                false
        );

        listing.setDecisionAt(
                LocalDateTime.now()
        );

        log.info(
                "Listing {} for bot {} was manually skipped by user",
                listingId,
                botId
        );

        return listingMapper.map(
                listing
        );
    }

    private Listing findActionRequiredListing(
            Long botId,
            Long listingId
    ) {

        Listing listing =
                listingRepository
                        .findByIdAndBotId(
                                listingId,
                                botId
                        )
                        .orElseThrow(
                                () -> new NoSuchElementException(
                                        "Listing "
                                                + listingId
                                                + " was not found for bot "
                                                + botId
                                )
                        );

        if (
                listing.getStatus()
                        != ListingStatus.ACTION_REQUIRED
        ) {

            throw new IllegalStateException(
                    "Listing "
                            + listingId
                            + " must have ACTION_REQUIRED status. "
                            + "Current status: "
                            + listing.getStatus()
            );
        }

        return listing;
    }

    public List<ListingResponse> discoverListings(
            Long botId,
            DiscoverListingsRequest request
    ) {

        validateBotExists(
                botId
        );

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
                        listingMapper.map(
                                claimedListing
                        )
                );

            } catch (DataIntegrityViolationException exception) {

                if (!isUniqueConstraintViolation(
                        exception
                )) {

                    log.error(
                            "Database integrity error while claiming listing {}",
                            listingRequest.getListingId(),
                            exception
                    );

                    throw exception;

                }

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

        Bot bot =
                botRepository.findById(
                                botId
                        )
                        .orElseThrow(
                                () -> new BotNotFoundException(
                                        botId
                                )
                        );

        Listing listing =
                Listing.builder()
                        .listingId(
                                request.getListingId()
                        )
                        .title(
                                request.getTitle()
                        )
                        .url(
                                request.getUrl()
                        )
                        .originalPrice(
                                request.getOriginalPrice()
                        )
                        .currentPrice(
                                request.getOriginalPrice()
                        )
                        .currentStep(1)
                        .awaitingSellerResponse(false)
                        .status(
                                ListingStatus.NEGOTIATING
                        )
                        .bot(bot)
                        .build();

        Listing savedListing =
                listingRepository.save(
                        listing
                );

        return listingMapper.map(
                savedListing
        );

    }

    @Transactional
    public ListingResponse updateListing(
            Long botId,
            Long listingId,
            UpdateListingRequest request
    ) {

        Listing listing =
                listingRepository.findByIdAndBotId(
                                listingId,
                                botId
                        )
                        .orElseThrow(
                                () -> new NoSuchElementException(
                                        "Listing "
                                                + listingId
                                                + " was not found for bot "
                                                + botId
                                )
                        );

        listing.setCurrentPrice(
                request.getCurrentPrice()
        );

        listing.setCurrentStep(
                request.getCurrentStep()
        );

        listing.setAwaitingSellerResponse(
                request.getAwaitingSellerResponse()
        );

        listing.setConversationId(
                request.getConversationId()
        );

        listing.setConversationUrl(
                request.getConversationUrl()
        );

        listing.setStatus(
                request.getStatus()
        );

        return listingMapper.map(
                listing
        );

    }

    private List<ListingResponse> getListingsByStatus(
            Long botId,
            ListingStatus status
    ) {

        validateBotExists(
                botId
        );

        return listingRepository
                .findByBotIdAndStatusOrderByIdAsc(
                        botId,
                        status
                )
                .stream()
                .map(
                        listingMapper::map
                )
                .toList();

    }

    private void validateBotExists(
            Long botId
    ) {

        if (!botRepository.existsById(
                botId
        )) {

            throw new BotNotFoundException(
                    botId
            );

        }

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

    private boolean isUniqueConstraintViolation(
            Throwable exception
    ) {

        Throwable currentCause =
                exception;

        while (currentCause != null) {

            if (currentCause
                    instanceof SQLException sqlException
                    && UNIQUE_VIOLATION_SQL_STATE.equals(
                    sqlException.getSQLState()
            )) {

                return true;

            }

            currentCause =
                    currentCause.getCause();

        }

        return false;

    }

}