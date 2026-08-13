package pl.flipbot.negotiation.guard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.flipbot.listing.Listing;
import pl.flipbot.listing.ListingRepository;
import pl.flipbot.listing.ListingStatus;
import pl.flipbot.negotiation.guard.dto.AcquireRealActionGuardRequest;
import pl.flipbot.negotiation.guard.dto.RealActionGuardResponse;
import pl.flipbot.negotiation.guard.dto.ReleaseRealActionGuardRequest;
import pl.flipbot.negotiation.guard.dto.ReleaseRealActionGuardResponse;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class RealActionGuardService {

    private final ListingRepository listingRepository;
    private final RealActionGuardRepository realActionGuardRepository;

    @Transactional
    public RealActionGuardResponse acquire(
            Long botId,
            Long listingId,
            AcquireRealActionGuardRequest request
    ) {
        Listing listing = lockListing(botId, listingId);

        RealActionGuard requestReplay =
                realActionGuardRepository.findByRequestId(request.requestId())
                        .orElse(null);

        if (requestReplay != null) {
            validateReplay(requestReplay, listing, request);

            if (isConfirmedByListingState(requestReplay, listing)) {
                log.warn(
                        "[REAL ACTION GUARD] Replay request {} refers to an action already confirmed by listing state. "
                                + "Removing stale guard and refusing reacquisition. Bot={}, listing={}, action={}, step={}, status={}, currentStep={}",
                        request.requestId(),
                        botId,
                        listingId,
                        requestReplay.getActionType(),
                        requestReplay.getStepNumber(),
                        listing.getStatus(),
                        listing.getCurrentStep()
                );

                realActionGuardRepository.delete(requestReplay);
                realActionGuardRepository.flush();

                return new RealActionGuardResponse(
                        false,
                        true,
                        null,
                        requestReplay.getActionType(),
                        requestReplay.getStepNumber(),
                        requestReplay.getCreatedAt()
                );
            }

            validateActionAgainstListing(listing, request);

            log.info(
                    "[REAL ACTION GUARD] Replayed acquisition for bot {}, listing {}, action {}, step {}, requestId={}",
                    botId,
                    listingId,
                    request.actionType(),
                    request.stepNumber(),
                    request.requestId()
            );

            return toResponse(requestReplay, true, true);
        }

        RealActionGuard activeGuard =
                realActionGuardRepository.findByListing_Id(listingId)
                        .orElse(null);

        if (activeGuard != null
                && isConfirmedByListingState(activeGuard, listing)) {

            log.warn(
                    "[REAL ACTION GUARD] Removing confirmed stale guard for bot {}, listing {}, action {}, step {}, requestId={}. Listing status={}, currentStep={}",
                    botId,
                    listingId,
                    activeGuard.getActionType(),
                    activeGuard.getStepNumber(),
                    activeGuard.getRequestId(),
                    listing.getStatus(),
                    listing.getCurrentStep()
            );

            realActionGuardRepository.delete(activeGuard);
            realActionGuardRepository.flush();
            activeGuard = null;
        }

        if (activeGuard != null) {
            log.warn(
                    "[REAL ACTION GUARD] Acquisition blocked for bot {}, listing {}. Existing action={}, step={}, createdAt={}",
                    botId,
                    listingId,
                    activeGuard.getActionType(),
                    activeGuard.getStepNumber(),
                    activeGuard.getCreatedAt()
            );

            return new RealActionGuardResponse(
                    false,
                    false,
                    null,
                    activeGuard.getActionType(),
                    activeGuard.getStepNumber(),
                    activeGuard.getCreatedAt()
            );
        }

        validateActionAgainstListing(listing, request);

        RealActionGuard guard =
                RealActionGuard.builder()
                        .listing(listing)
                        .requestId(request.requestId())
                        .actionType(request.actionType())
                        .stepNumber(request.stepNumber())
                        .createdAt(LocalDateTime.now())
                        .build();

        RealActionGuard saved = realActionGuardRepository.saveAndFlush(guard);

        log.warn(
                "[REAL ACTION GUARD] ACQUIRED for bot {}, listing {}, action {}, step {}, requestId={}",
                botId,
                listingId,
                saved.getActionType(),
                saved.getStepNumber(),
                saved.getRequestId()
        );

        return toResponse(saved, true, false);
    }

    @Transactional
    public ReleaseRealActionGuardResponse release(
            Long botId,
            Long listingId,
            ReleaseRealActionGuardRequest request
    ) {
        lockListing(botId, listingId);

        RealActionGuard guard =
                realActionGuardRepository.findByListing_Id(listingId)
                        .orElse(null);

        if (guard == null) {
            return new ReleaseRealActionGuardResponse(
                    false,
                    true
            );
        }

        if (!Objects.equals(guard.getRequestId(), request.requestId())) {
            throw new IllegalStateException(
                    "Real action guard for listing "
                            + listingId
                            + " belongs to a different requestId"
            );
        }

        realActionGuardRepository.delete(guard);
        realActionGuardRepository.flush();

        log.info(
                "[REAL ACTION GUARD] RELEASED for bot {}, listing {}, action {}, step {}, requestId={}",
                botId,
                listingId,
                guard.getActionType(),
                guard.getStepNumber(),
                guard.getRequestId()
        );

        return new ReleaseRealActionGuardResponse(
                true,
                false
        );
    }

    private Listing lockListing(
            Long botId,
            Long listingId
    ) {
        return listingRepository.findByIdAndBotIdForUpdate(
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
    }

    private void validateReplay(
            RealActionGuard guard,
            Listing listing,
            AcquireRealActionGuardRequest request
    ) {
        if (!Objects.equals(guard.getListing().getId(), listing.getId())
                || guard.getActionType() != request.actionType()
                || !Objects.equals(guard.getStepNumber(), request.stepNumber())) {

            throw new IllegalStateException(
                    "Real action guard requestId "
                            + request.requestId()
                            + " is already associated with another action"
            );
        }
    }

    private void validateActionAgainstListing(
            Listing listing,
            AcquireRealActionGuardRequest request
    ) {
        switch (request.actionType()) {
            case FIRST_OFFER -> {
                if (listing.getStatus() != ListingStatus.DISCOVERED) {
                    throw new IllegalStateException(
                            "FIRST_OFFER guard requires DISCOVERED listing. Current status: "
                                    + listing.getStatus()
                    );
                }

                if (request.stepNumber() != 1) {
                    throw new IllegalStateException(
                            "FIRST_OFFER guard requires stepNumber=1"
                    );
                }
            }

            case NEXT_STEP -> {
                if (listing.getStatus() != ListingStatus.NEGOTIATING) {
                    throw new IllegalStateException(
                            "NEXT_STEP guard requires NEGOTIATING listing. Current status: "
                                    + listing.getStatus()
                    );
                }

                Integer currentStep = listing.getCurrentStep();

                if (currentStep == null
                        || request.stepNumber() != currentStep + 1) {

                    throw new IllegalStateException(
                            "NEXT_STEP guard requires exactly currentStep+1. Current step: "
                                    + currentStep
                                    + ", requested step: "
                                    + request.stepNumber()
                    );
                }
            }
        }
    }

    private boolean isConfirmedByListingState(
            RealActionGuard guard,
            Listing listing
    ) {
        if (listing.getStatus() != ListingStatus.NEGOTIATING) {
            return false;
        }

        Integer currentStep = listing.getCurrentStep();

        if (currentStep == null) {
            return false;
        }

        return switch (guard.getActionType()) {
            case FIRST_OFFER -> currentStep >= 1;
            case NEXT_STEP -> currentStep >= guard.getStepNumber();
        };
    }

    private RealActionGuardResponse toResponse(
            RealActionGuard guard,
            boolean acquired,
            boolean replayed
    ) {
        return new RealActionGuardResponse(
                acquired,
                replayed,
                guard.getRequestId(),
                guard.getActionType(),
                guard.getStepNumber(),
                guard.getCreatedAt()
        );
    }
}
