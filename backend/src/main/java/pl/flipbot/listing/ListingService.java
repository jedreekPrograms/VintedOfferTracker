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
import pl.flipbot.listing.dto.NegotiationActivityRequest;
import pl.flipbot.listing.dto.NegotiationActivityResponse;
import pl.flipbot.listing.dto.UpdateListingRequest;
import pl.flipbot.mapper.ListingMapper;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ListingService {

    private static final String UNIQUE_VIOLATION_SQL_STATE = "23505";

    private final ListingRepository listingRepository;
    private final BotRepository botRepository;
    private final ListingMapper listingMapper;
    private final ListingClaimService listingClaimService;

    public List<ListingResponse> getDiscoveredListings(Long botId) {
        return getListingsByStatus(botId, ListingStatus.DISCOVERED);
    }

    public List<ListingResponse> getNegotiatingListings(Long botId) {
        return getListingsByStatus(botId, ListingStatus.NEGOTIATING);
    }

    public List<ListingResponse> getActionRequiredListings(Long botId) {
        return getListingsByStatus(botId, ListingStatus.ACTION_REQUIRED);
    }

    public List<ListingResponse> getPurchasedListings(Long botId) {
        return getListingsByStatus(botId, ListingStatus.PURCHASED);
    }

    public List<ListingResponse> getSkippedByUserListings(Long botId) {
        return getListingsByStatus(botId, ListingStatus.SKIPPED_BY_USER);
    }

    @Transactional
    public ListingResponse markAsPurchased(Long botId, Long listingId) {
        Listing listing = findActionRequiredListing(botId, listingId);
        listing.setStatus(ListingStatus.PURCHASED);
        listing.setAwaitingSellerResponse(false);
        listing.setDecisionAt(LocalDateTime.now());

        log.info("Listing {} for bot {} was manually marked as PURCHASED", listingId, botId);
        return listingMapper.map(listing);
    }

    @Transactional
    public ListingResponse skipByUser(Long botId, Long listingId) {
        Listing listing = findActionRequiredListing(botId, listingId);
        listing.setStatus(ListingStatus.SKIPPED_BY_USER);
        listing.setAwaitingSellerResponse(false);
        listing.setDecisionAt(LocalDateTime.now());

        log.info("Listing {} for bot {} was manually skipped by user", listingId, botId);
        return listingMapper.map(listing);
    }

    private Listing findActionRequiredListing(Long botId, Long listingId) {
        Listing listing = listingRepository.findByIdAndBotId(listingId, botId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Listing " + listingId + " was not found for bot " + botId
                ));

        if (listing.getStatus() != ListingStatus.ACTION_REQUIRED) {
            throw new IllegalStateException(
                    "Listing " + listingId + " must have ACTION_REQUIRED status. Current status: "
                            + listing.getStatus()
            );
        }

        return listing;
    }

    public List<ListingResponse> discoverListings(Long botId, DiscoverListingsRequest request) {
        validateBotExists(botId);

        Map<String, CreateListingRequest> uniqueRequests =
                removeDuplicatedRequests(request.getListings());

        if (uniqueRequests.isEmpty()) {
            return List.of();
        }

        Set<String> existingListingIds = findExistingListingIds(
                botId,
                uniqueRequests.keySet()
        );
        List<ListingResponse> claimedListings = new ArrayList<>();

        for (CreateListingRequest listingRequest : uniqueRequests.values()) {
            if (existingListingIds.contains(listingRequest.getListingId())) {
                continue;
            }

            try {
                Listing claimedListing = listingClaimService.claimListing(botId, listingRequest);
                claimedListings.add(listingMapper.map(claimedListing));
            } catch (DataIntegrityViolationException exception) {
                if (!isUniqueConstraintViolation(exception)) {
                    log.error(
                            "Database integrity error while claiming listing {} for bot {}",
                            listingRequest.getListingId(),
                            botId,
                            exception
                    );
                    throw exception;
                }

                log.debug(
                        "Listing {} was claimed concurrently for bot {} by another worker",
                        listingRequest.getListingId(),
                        botId
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
    public ListingResponse createListing(Long botId, CreateListingRequest request) {
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
                .currentStepStartedAt(LocalDateTime.now())
                .bot(bot)
                .build();

        return listingMapper.map(listingRepository.save(listing));
    }

    @Transactional
    public ListingResponse updateListing(
            Long botId,
            Long listingId,
            UpdateListingRequest request
    ) {
        Listing listing = listingRepository.findByIdAndBotId(listingId, botId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Listing " + listingId + " was not found for bot " + botId
                ));

        ListingStatus previousStatus = listing.getStatus();
        Integer previousStep = listing.getCurrentStep();

        boolean negotiationStepStarted =
                request.getStatus() == ListingStatus.NEGOTIATING
                        && (previousStatus != ListingStatus.NEGOTIATING
                        || !Objects.equals(previousStep, request.getCurrentStep()));

        listing.setCurrentPrice(request.getCurrentPrice());
        listing.setCurrentStep(request.getCurrentStep());
        listing.setAwaitingSellerResponse(request.getAwaitingSellerResponse());
        listing.setConversationId(request.getConversationId());
        listing.setConversationUrl(request.getConversationUrl());
        listing.setStatus(request.getStatus());

        if (request.getStatus() == ListingStatus.EXPIRED) {
            listing.setDecisionAt(LocalDateTime.now());
        }

        if (negotiationStepStarted) {
            LocalDateTime now = LocalDateTime.now();
            listing.setCurrentStepStartedAt(now);

            /*
             * Every negotiation step owns its own activity and response clocks.
             * Neither a read/message nor a rejection/counteroffer from the old
             * step may influence timing of the new step.
             */
            listing.setSellerActivityAt(null);
            listing.setReadDetectedAt(null);
            listing.setFormalResponseFingerprint(null);
            listing.setFormalResponseDetectedAt(null);

            log.info(
                    "Listing {} for bot {} started negotiation step {} at {}. "
                            + "Seller activity and formal-response timers were reset.",
                    listingId,
                    botId,
                    request.getCurrentStep(),
                    now
            );
        }

        return listingMapper.map(listing);
    }

    @Transactional
    public NegotiationActivityResponse recordNegotiationActivity(
            Long botId,
            Long listingId,
            NegotiationActivityRequest request
    ) {
        Listing listing = listingRepository.findByIdAndBotId(listingId, botId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Listing " + listingId + " was not found for bot " + botId
                ));

        if (listing.getStatus() != ListingStatus.NEGOTIATING) {
            throw new IllegalStateException(
                    "Negotiation activity can only be recorded for NEGOTIATING listings. Listing "
                            + listingId + " currently has status " + listing.getStatus()
            );
        }

        LocalDateTime sellerActivityAt = request.sellerActivityAt();
        if (sellerActivityAt != null) {
            boolean belongsToCurrentStep = listing.getCurrentStepStartedAt() == null
                    || !sellerActivityAt.isBefore(listing.getCurrentStepStartedAt());

            if (!belongsToCurrentStep) {
                log.debug(
                        "Ignoring stale seller activity {} for listing {} because current step {} started at {}",
                        sellerActivityAt,
                        listingId,
                        listing.getCurrentStep(),
                        listing.getCurrentStepStartedAt()
                );
            } else if (listing.getSellerActivityAt() == null
                    || sellerActivityAt.isAfter(listing.getSellerActivityAt())) {
                listing.setSellerActivityAt(sellerActivityAt);
                log.info(
                        "Listing {} for bot {} recorded seller activity at {}",
                        listingId,
                        botId,
                        sellerActivityAt
                );
            }
        }

        if (request.readDetected() && listing.getReadDetectedAt() == null) {
            LocalDateTime readDetectedAt = LocalDateTime.now();
            listing.setReadDetectedAt(readDetectedAt);
            log.info(
                    "Listing {} for bot {} recorded first read detection at {}",
                    listingId,
                    botId,
                    readDetectedAt
            );
        }

        String formalResponseFingerprint = normalizeOptionalText(
                request.formalResponseFingerprint()
        );

        if (formalResponseFingerprint != null
                && !Objects.equals(
                formalResponseFingerprint,
                listing.getFormalResponseFingerprint()
        )) {
            LocalDateTime detectedAt = LocalDateTime.now();
            listing.setFormalResponseFingerprint(formalResponseFingerprint);
            listing.setFormalResponseDetectedAt(detectedAt);

            log.info(
                    "Listing {} for bot {} recorded formal response '{}' for step {} at {}",
                    listingId,
                    botId,
                    formalResponseFingerprint,
                    listing.getCurrentStep(),
                    detectedAt
            );
        }

        return mapNegotiationActivity(listing);
    }

    private NegotiationActivityResponse mapNegotiationActivity(Listing listing) {
        return new NegotiationActivityResponse(
                listing.getId(),
                listing.getCurrentStep(),
                listing.getCurrentStepStartedAt(),
                listing.getSellerActivityAt(),
                listing.getReadDetectedAt(),
                listing.getFormalResponseFingerprint(),
                listing.getFormalResponseDetectedAt()
        );
    }

    private List<ListingResponse> getListingsByStatus(Long botId, ListingStatus status) {
        validateBotExists(botId);
        return listingRepository.findByBotIdAndStatusOrderByIdAsc(botId, status)
                .stream()
                .map(listingMapper::map)
                .toList();
    }

    private void validateBotExists(Long botId) {
        if (!botRepository.existsById(botId)) {
            throw new BotNotFoundException(botId);
        }
    }

    private Map<String, CreateListingRequest> removeDuplicatedRequests(
            List<CreateListingRequest> requests
    ) {
        Map<String, CreateListingRequest> uniqueRequests = new LinkedHashMap<>();
        for (CreateListingRequest request : requests) {
            uniqueRequests.putIfAbsent(request.getListingId(), request);
        }
        return uniqueRequests;
    }

    private Set<String> findExistingListingIds(
            Long botId,
            Set<String> listingIds
    ) {
        List<Listing> existingListings =
                listingRepository.findAllByBotIdAndListingIdIn(
                        botId,
                        listingIds
                );
        Set<String> existingListingIds = new HashSet<>();
        for (Listing listing : existingListings) {
            existingListingIds.add(listing.getListingId());
        }
        return existingListingIds;
    }

    private String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private boolean isUniqueConstraintViolation(Throwable exception) {
        Throwable currentCause = exception;
        while (currentCause != null) {
            if (currentCause instanceof SQLException sqlException
                    && UNIQUE_VIOLATION_SQL_STATE.equals(sqlException.getSQLState())) {
                return true;
            }
            currentCause = currentCause.getCause();
        }
        return false;
    }
}
