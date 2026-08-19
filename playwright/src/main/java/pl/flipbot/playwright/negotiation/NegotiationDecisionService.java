package pl.flipbot.playwright.negotiation;

import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.model.BotConfigurationDto;
import pl.flipbot.playwright.model.NegotiationStepDto;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
public class NegotiationDecisionService {

    private final AdaptiveNegotiationPricingService pricingService =
            new AdaptiveNegotiationPricingService();

    public NegotiationDecision decide(
            ListingResponseDto listing,
            NegotiationConversationSnapshot snapshot,
            BotConfigurationDto configuration
    ) {
        Objects.requireNonNull(listing, "Listing cannot be null");
        Objects.requireNonNull(snapshot, "Conversation snapshot cannot be null");
        Objects.requireNonNull(configuration, "Bot configuration cannot be null");

        validateListing(listing);
        validateNegotiationSteps(configuration.getNegotiationSteps());

        NegotiationDecision decision = switch (snapshot.result()) {
            case PENDING -> NegotiationDecision.waitForSeller();
            case ACCEPTED -> NegotiationDecision.actionRequiredAfterAcceptance();
            case REJECTED -> decideAfterRejection(listing, configuration);
            case SELLER_COUNTER_OFFER -> decideAfterSellerCounterOffer(
                    listing,
                    snapshot,
                    configuration
            );
            case UNKNOWN -> NegotiationDecision.unknown();
        };

        logDecision(listing, snapshot, decision);
        return decision;
    }

    private NegotiationDecision decideAfterRejection(
            ListingResponseDto listing,
            BotConfigurationDto configuration
    ) {
        Optional<NegotiationStepDto> configuredNextStep = findNextStep(
                listing.currentStep(),
                configuration.getNegotiationSteps()
        );

        if (configuredNextStep.isEmpty()) {
            return NegotiationDecision.rejected(
                    null,
                    "The seller rejected the latest offer and there are no more negotiation steps"
            );
        }

        Optional<NegotiationStepDto> effectiveNextStep =
                pricingService.adaptNextStep(
                        listing,
                        configuredNextStep.get(),
                        configuration
                );

        if (effectiveNextStep.isPresent()) {
            return NegotiationDecision.sendNextStep(
                    effectiveNextStep.get(),
                    null,
                    "The seller rejected the latest offer and another negotiation step is available"
            );
        }

        return NegotiationDecision.rejected(
                null,
                "The seller rejected the latest offer, but the next adaptive offer would exceed the global negotiation cap "
                        + configuration.getMaxAutomaticOffer()
        );
    }

    private NegotiationDecision decideAfterSellerCounterOffer(
            ListingResponseDto listing,
            NegotiationConversationSnapshot snapshot,
            BotConfigurationDto configuration
    ) {
        BigDecimal sellerCounterOfferPrice = snapshot.sellerCounterOfferPrice();

        if (sellerCounterOfferPrice == null) {
            throw new IllegalStateException(
                    "Snapshot is classified as SELLER_COUNTER_OFFER, but contains no seller counteroffer price"
            );
        }

        NegotiationStepDto currentStep = findCurrentStep(
                listing.currentStep(),
                configuration.getNegotiationSteps()
        );

        BigDecimal maxAcceptedCounterOffer =
                pricingService.effectiveAcceptedCounterOfferLimit(
                        listing,
                        currentStep,
                        configuration
                );

        if (maxAcceptedCounterOffer != null
                && sellerCounterOfferPrice.compareTo(maxAcceptedCounterOffer) <= 0) {
            return NegotiationDecision.actionRequiredForCounterOffer(
                    sellerCounterOfferPrice
            );
        }

        Optional<NegotiationStepDto> configuredNextStep = findNextStep(
                listing.currentStep(),
                configuration.getNegotiationSteps()
        );

        if (configuredNextStep.isPresent()) {
            Optional<NegotiationStepDto> effectiveNextStep =
                    pricingService.adaptNextStep(
                            listing,
                            configuredNextStep.get(),
                            configuration
                    );

            if (effectiveNextStep.isPresent()) {
                String reason = maxAcceptedCounterOffer == null
                        ? "The current step has no accepted counteroffer limit and another negotiation step is available"
                        : "The seller counteroffer "
                        + sellerCounterOfferPrice
                        + " exceeds the effective accepted limit "
                        + maxAcceptedCounterOffer
                        + " and another negotiation step is available";

                return NegotiationDecision.sendNextStep(
                        effectiveNextStep.get(),
                        sellerCounterOfferPrice,
                        reason
                );
            }

            return NegotiationDecision.rejected(
                    sellerCounterOfferPrice,
                    "The seller counteroffer "
                            + sellerCounterOfferPrice
                            + " is above the accepted limit, and the next adaptive offer would exceed the global negotiation cap "
                            + configuration.getMaxAutomaticOffer()
            );
        }

        String reason = maxAcceptedCounterOffer == null
                ? "The current step has no accepted counteroffer limit and there are no more negotiation steps"
                : "The seller counteroffer "
                + sellerCounterOfferPrice
                + " exceeds the effective accepted limit "
                + maxAcceptedCounterOffer
                + " and there are no more negotiation steps";

        return NegotiationDecision.rejected(
                sellerCounterOfferPrice,
                reason
        );
    }

    private NegotiationStepDto findCurrentStep(
            Integer currentStepNumber,
            List<NegotiationStepDto> negotiationSteps
    ) {
        if (currentStepNumber == null) {
            throw new IllegalStateException("Negotiating listing has no current step");
        }

        return negotiationSteps.stream()
                .filter(Objects::nonNull)
                .filter(step -> Objects.equals(
                        step.getStepNumber(),
                        currentStepNumber
                ))
                .findFirst()
                .orElseThrow(
                        () -> new IllegalStateException(
                                "Cannot find negotiation configuration for current step "
                                        + currentStepNumber
                        )
                );
    }

    private Optional<NegotiationStepDto> findNextStep(
            Integer currentStepNumber,
            List<NegotiationStepDto> negotiationSteps
    ) {
        if (currentStepNumber == null) {
            throw new IllegalStateException("Negotiating listing has no current step");
        }

        return negotiationSteps.stream()
                .filter(Objects::nonNull)
                .filter(step -> step.getStepNumber() != null)
                .filter(step -> step.getStepNumber() > currentStepNumber)
                .min(Comparator.comparing(NegotiationStepDto::getStepNumber))
                .map(this::validateNextStep);
    }

    private NegotiationStepDto validateNextStep(NegotiationStepDto nextStep) {
        if (nextStep.getOfferPrice() == null) {
            throw new IllegalStateException(
                    "Negotiation step "
                            + nextStep.getStepNumber()
                            + " has no offer price"
            );
        }

        if (nextStep.getOfferPrice().signum() <= 0) {
            throw new IllegalStateException(
                    "Negotiation step "
                            + nextStep.getStepNumber()
                            + " has an invalid offer price: "
                            + nextStep.getOfferPrice()
            );
        }

        return nextStep;
    }

    private void validateListing(ListingResponseDto listing) {
        if (listing.id() == null) {
            throw new IllegalArgumentException("Backend listing ID cannot be null");
        }

        if (!"NEGOTIATING".equals(listing.status())) {
            throw new IllegalArgumentException(
                    "Negotiation decision can only be created for a NEGOTIATING listing. Backend listing: "
                            + listing.id()
                            + ", status: "
                            + listing.status()
            );
        }

        if (listing.currentStep() == null || listing.currentStep() <= 0) {
            throw new IllegalArgumentException(
                    "Negotiating listing "
                            + listing.id()
                            + " has an invalid current step: "
                            + listing.currentStep()
            );
        }
    }

    private void validateNegotiationSteps(
            List<NegotiationStepDto> negotiationSteps
    ) {
        if (negotiationSteps == null || negotiationSteps.isEmpty()) {
            throw new IllegalStateException("Bot configuration has no negotiation steps");
        }
    }

    private void logDecision(
            ListingResponseDto listing,
            NegotiationConversationSnapshot snapshot,
            NegotiationDecision decision
    ) {
        log.info(
                "[DECISION] Backend listing {}, marketplace listing {}, current step {}, conversation result {}, seller counteroffer {}, decision {}, next step {}, effective next price {}, reason: {}",
                listing.id(),
                listing.listingId(),
                listing.currentStep(),
                snapshot.result(),
                snapshot.sellerCounterOfferPrice(),
                decision.type(),
                decision.nextStep() == null
                        ? null
                        : decision.nextStep().getStepNumber(),
                decision.nextStep() == null
                        ? null
                        : decision.nextStep().getOfferPrice(),
                decision.reason()
        );
    }
}
