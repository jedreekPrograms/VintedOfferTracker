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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BotTargetListingService {

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

        Map<String, CreateListingRequest> uniqueRequests = new LinkedHashMap<>();
        for (CreateListingRequest listing : request.getListings()) {
            uniqueRequests.putIfAbsent(listing.getListingId(), listing);
        }

        if (uniqueRequests.isEmpty()) {
            return List.of();
        }

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

                if (!java.util.Objects.equals(existingTargetId, targetId)) {
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
                Listing created = listingClaimService.claimListing(
                        botId,
                        target,
                        listingRequest
                );
                result.add(listingMapper.map(created));
                claimed++;
            } catch (DataIntegrityViolationException exception) {
                log.debug(
                        "Marketplace listing {} was claimed concurrently for bot {}.",
                        listingRequest.getListingId(),
                        botId
                );
            }
        }

        log.info(
                "Bot {} additional target {} fresh scan contained {} listing(s): claimed {}, requalified {}, skipped {} overlap(s) already owned by another product, returned {}.",
                botId,
                targetId,
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
}
