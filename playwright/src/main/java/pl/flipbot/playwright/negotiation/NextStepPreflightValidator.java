package pl.flipbot.playwright.negotiation;

import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.model.BotConfigurationDto;
import pl.flipbot.playwright.model.NegotiationStepDto;

import java.math.BigDecimal;

/**
 * Pure preflight policy for next-step offers. No browser, network, quota or
 * persisted state mutations. The executor invokes these validations BEFORE
 * navigating to the chat or opening the price form.
 *
 * Comparison with the previous price and equality only at a positively
 * configured adaptive cap retain their existing fail-closed behavior.
 */
final class NextStepPreflightValidator {
    private final BotContext context;

    NextStepPreflightValidator(BotContext context) {
        this.context = context;
    }

    void validateListing(
            ListingResponseDto listing
    ) {

        if (listing.id() == null) {

            throw new IllegalArgumentException(
                    "Backend listing ID cannot be null"
            );

        }

        if (!"NEGOTIATING".equals(
                listing.status()
        )) {

            throw new IllegalArgumentException(
                    "Next negotiation step can only be processed "
                            + "for a NEGOTIATING listing. Backend listing: "
                            + listing.id()
                            + ", status: "
                            + listing.status()
            );

        }

        if (listing.currentStep() == null
                || listing.currentStep() <= 0) {

            throw new IllegalArgumentException(
                    "Negotiating listing "
                            + listing.id()
                            + " has an invalid current step: "
                            + listing.currentStep()
            );

        }

        if (listing.conversationId() == null
                || listing.conversationId().isBlank()) {

            throw new IllegalArgumentException(
                    "Negotiating listing "
                            + listing.id()
                            + " has no conversation ID"
            );

        }

        if (listing.conversationUrl() == null
                || listing.conversationUrl().isBlank()) {

            throw new IllegalArgumentException(
                    "Negotiating listing "
                            + listing.id()
                            + " has no conversation URL"
            );

        }

    }

    void validateNextStep(
            ListingResponseDto listing,
            NegotiationStepDto nextStep
    ) {

        if (nextStep.getStepNumber() == null) {

            throw new IllegalArgumentException(
                    "Next negotiation step has no step number"
            );

        }

        if (nextStep.getStepNumber()
                <= listing.currentStep()) {

            throw new IllegalArgumentException(
                    "Next negotiation step must be greater than "
                            + "the current step. Current: "
                            + listing.currentStep()
                            + ", next: "
                            + nextStep.getStepNumber()
            );

        }

        if (nextStep.getOfferPrice() == null) {

            throw new IllegalArgumentException(
                    "Negotiation step "
                            + nextStep.getStepNumber()
                            + " has no offer price"
            );

        }

        if (nextStep.getOfferPrice()
                .signum() <= 0) {

            throw new IllegalArgumentException(
                    "Negotiation step "
                            + nextStep.getStepNumber()
                            + " has an invalid offer price: "
                            + nextStep.getOfferPrice()
            );

        }

        if (listing.currentPrice() != null) {
            int priceComparison =
                    nextStep.getOfferPrice().compareTo(listing.currentPrice());

            if (priceComparison < 0) {
                throw new IllegalArgumentException(
                        "Next negotiation offer cannot be lower than the current offer. Current price: "
                                + listing.currentPrice()
                                + ", next price: "
                                + nextStep.getOfferPrice()
                );
            }

            if (priceComparison == 0
                    && !isAllowedAdaptiveCapPlateau(listing, nextStep)) {
                throw new IllegalArgumentException(
                        "Next negotiation offer may equal the current offer only after the adaptive global cap has been reached. Current price: "
                                + listing.currentPrice()
                                + ", next price: "
                                + nextStep.getOfferPrice()
                );
            }
        }

    }

    boolean isAllowedAdaptiveCapPlateau(
            ListingResponseDto listing,
            NegotiationStepDto nextStep
    ) {
        if (listing == null
                || nextStep == null
                || listing.currentPrice() == null
                || nextStep.getOfferPrice() == null
                || context.getBot() == null) {
            return false;
        }

        BotConfigurationDto configuration =
                context.getBot().getConfiguration();

        if (configuration == null
                || !Boolean.TRUE.equals(
                configuration.getAutoRaiseOfferToVintedMinimum()
        )
                || configuration.getMaxAutomaticOffer() == null
                || configuration.getMaxAutomaticOffer().signum() <= 0) {
            return false;
        }

        BigDecimal cap = configuration.getMaxAutomaticOffer();

        return listing.currentPrice().compareTo(cap) == 0
                && nextStep.getOfferPrice().compareTo(cap) == 0
                && listing.currentStep() != null
                && nextStep.getStepNumber() != null
                && nextStep.getStepNumber() > listing.currentStep();
    }
}
