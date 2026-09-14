package pl.flipbot.listing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.flipbot.bot.BotRepository;
import pl.flipbot.bot.configuration.BotAdditionalTarget;
import pl.flipbot.bot.configuration.BotAdditionalTargetRepository;
import pl.flipbot.exception.BotNotFoundException;
import pl.flipbot.listing.dto.CreateListingRequest;
import pl.flipbot.listing.dto.DiscoverListingsRequest;
import pl.flipbot.listing.dto.ListingResponse;
import pl.flipbot.mapper.ListingMapper;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BotTargetListingService {

    private static final String UNIQUE_VIOLATION_SQL_STATE = "23505";

    private final ListingRepository listingRepository;
    private final ListingClaimService listingClaimService;
    private final ListingRediscoveryService listingRediscoveryService;
    private final ListingMapper listingMapper;
    private final BotRepository botRepository;
    private final BotAdditionalTargetRepository additionalTargetRepository;

    @Transactional(readOnly = true)
    public List<ListingResponse> getPrimaryDiscovered(Long botId) {
        validateBotExists(botId);
        return listingRepository
                .findByBotIdAndStatusAndAdditionalTargetIsNullOrderByIdAsc(
                        botId,
                        ListingStatus.DISCOVERED
                )
                .stream()
                .map(listingMapper::map)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ListingResponse> getAdditionalTargetDiscovered(
            Long botId,
            Long targetId
    ) {
        requireTarget(botId, targetId);
        return listingRepository
                .findByBotIdAndStatusAndAdditionalTargetIdOrderByIdAsc(
                        botId,
                        ListingStatus.DISCOVERED,
                        targetId
                )
                .stream()
                .map(listingMapper::map)
                .toList();
    }

    public List<ListingResponse> discoverPrimary(
            Long botId,
            DiscoverListingsRequest request
    ) {
        validateBotExists(botId);
        return discoverForTarget(botId, null, request);
    }

    public List<ListingResponse> discoverAdditionalTarget(
            Long botId,
            Long targetId,
            DiscoverListingsRequest request
    ) {
        validateBotExists(botId);
        BotAdditionalTarget target = requireTarget(botId, targetId);

        if (!Boolean.TRUE.equals(target.getActive())) {
            throw new IllegalStateException(
                    "Dodatkowy produkt " + targetId + " jest wyłączony."
            );
        }

        return discoverForTarget(botId, target, request);
    }

    private List<ListingResponse> discoverForTarget(
            Long botId,
            BotAdditionalTarget target,
            DiscoverListingsRequest request
    ) {
        Map<String, CreateListingRequest> uniqueRequests = new LinkedHashMap<>();
        for (CreateListingRequest listing : request.getListings()) {
            uniqueRequests.putIfAbsent(listing.getListingId(), listing);
        }

        if (uniqueRequests.isEmpty()) {
            return List.of();
        }

        Long expectedTargetId = target == null ? null : target.getId();
        String targetLabel = expectedTargetId == null
                ? "MAIN"
                : expectedTargetId.toString();

        Map<String, Listing> existingById = new LinkedHashMap<>();
        for (Listing listing : listingRepository.findAllByBotIdAndListingIdIn(
                botId,
                uniqueRequests.keySet()
        )) {
            existingById.put(listing.getListingId(), listing);
        }

        List<ListingResponse> result = new ArrayList<>();
        int claimed = 0;
        int requalified = 0;
        int overlaps = 0;

        for (CreateListingRequest listingRequest : uniqueRequests.values()) {
            Listing existing = existingById.get(listingRequest.getListingId());
            if (existing != null) {
                Long existingTargetId = existing.getAdditionalTarget() == null
                        ? null
                        : existing.getAdditionalTarget().getId();

                if (!Objects.equals(existingTargetId, expectedTargetId)) {
                    overlaps++;
                    continue;
                }

                if (listingRediscoveryService.shouldAttemptRequalification(existing)) {
                    Optional<Listing> restored = listingRediscoveryService
                            .requalifyIfEligible(
                                    botId,
                                    listingRequest.getListingId(),
                                    listingRequest
                            );
                    if (restored.isPresent()) {
                        result.add(listingMapper.map(restored.get()));
                        requalified++;
                    }
                }
                continue;
            }

            try {
                Listing created = target == null
                        ? listingClaimService.claimListing(botId, listingRequest)
                        : listingClaimService.claimListing(botId, target, listingRequest);
                result.add(listingMapper.map(created));
                claimed++;
            } catch (DataIntegrityViolationException exception) {
                if (!isUniqueConstraintViolation(exception)) {
                    throw exception;
                }
                log.debug(
                        "Marketplace listing {} was claimed concurrently for bot {} while processing target {}.",
                        listingRequest.getListingId(),
                        botId,
                        targetLabel
                );
            }
        }

        log.info(
                "Bot {} target {} fresh scan contained {} listing(s): claimed {}, requalified {}, skipped {} overlap(s) already owned by another product, returned {}.",
                botId,
                targetLabel,
                uniqueRequests.size(),
                claimed,
                requalified,
                overlaps,
                result.size()
        );

        return result;
    }

    private BotAdditionalTarget requireTarget(Long botId, Long targetId) {
        return additionalTargetRepository
                .findByIdAndConfigurationBotId(targetId, botId)
                .orElseThrow(() -> new java.util.NoSuchElementException(
                        "Nie znaleziono dodatkowego produktu " + targetId
                                + " dla bota " + botId + "."
                ));
    }

    private void validateBotExists(Long botId) {
        if (!botRepository.existsById(botId)) {
            throw new BotNotFoundException(botId);
        }
    }

    private boolean isUniqueConstraintViolation(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof SQLException sqlException
                    && UNIQUE_VIOLATION_SQL_STATE.equals(sqlException.getSQLState())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
