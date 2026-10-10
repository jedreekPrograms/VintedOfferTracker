package pl.flipbot.playwright.negotiation;

import pl.flipbot.playwright.api.listing.ListingClient;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.api.listing.dto.UpdateListingRequestDto;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.model.NegotiationStepDto;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Persists ONLY an offer already confirmed in the Vinted conversation.
 * Called exactly after visible new-own-offer evidence and before attempting
 * the optional message, so a failed chat message cannot resubmit the price.
 *
 * Retains the original backend response assertions on status, step and
 * canonical conversation, and persists the Vinted-displayed amount rather
 * than the configured amount (cross-currency safety).
 */
final class NextStepBackendPersistence {
    private final BotContext context;
    private final ListingClient listingClient;

    NextStepBackendPersistence(BotContext context, ListingClient listingClient) {
        this.context = context;
        this.listingClient = listingClient;
    }

    ListingResponseDto markNextStepStarted(
            ListingResponseDto listing,
            NegotiationStepDto nextStep,
            BigDecimal displayedPrice
    ) {

        UpdateListingRequestDto request =
                new UpdateListingRequestDto(
                        "NEGOTIATING",
                        displayedPrice,
                        nextStep.getStepNumber(),
                        true,
                        listing.conversationId(),
                        listing.conversationUrl()
                );

        ListingResponseDto updatedListing =
                listingClient.updateListing(
                        context.getBot().getId(),
                        listing.id(),
                        request
                );

        if (!"NEGOTIATING".equals(
                updatedListing.status()
        )) {

            throw new IllegalStateException(
                    "Backend returned an unexpected status after "
                            + "sending the next negotiation step. Expected "
                            + "NEGOTIATING, actual: "
                            + updatedListing.status()
            );

        }

        if (!Objects.equals(
                nextStep.getStepNumber(),
                updatedListing.currentStep()
        )) {

            throw new IllegalStateException(
                    "Backend returned an unexpected current step. Expected: "
                            + nextStep.getStepNumber()
                            + ", actual: "
                            + updatedListing.currentStep()
            );

        }

        if (!Objects.equals(
                listing.conversationId(),
                updatedListing.conversationId()
        )) {

            throw new IllegalStateException(
                    "Backend returned an unexpected conversation ID. "
                            + "Expected: "
                            + listing.conversationId()
                            + ", actual: "
                            + updatedListing.conversationId()
            );

        }

        return updatedListing;

    }
}
