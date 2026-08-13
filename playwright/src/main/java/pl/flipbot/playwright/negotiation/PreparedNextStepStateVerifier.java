package pl.flipbot.playwright.negotiation;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.model.NegotiationStepDto;
import pl.flipbot.playwright.verification.HumanVerificationHandler;

import java.net.URI;
import java.util.Objects;

@Slf4j
@RequiredArgsConstructor
public class PreparedNextStepStateVerifier {

    private static final double ELEMENT_TIMEOUT_MS = 15_000;

    private final BotContext context;
    private final HumanVerificationHandler humanVerificationHandler =
            new HumanVerificationHandler();

    public void verify(
            ListingResponseDto listing,
            NegotiationStepDto nextStep
    ) {
        Objects.requireNonNull(listing, "Listing cannot be null");
        Objects.requireNonNull(nextStep, "Next negotiation step cannot be null");

        Page page = context.getPage();
        humanVerificationHandler.waitUntilVerified(page);

        String openedConversationId = extractConversationId(page.url());
        if (!listing.conversationId().equals(openedConversationId)) {
            throw new IllegalStateException(
                    "Prepared next-step form belongs to an unexpected conversation"
            );
        }

        Locator priceInput =
                page.getByTestId(NegotiationSelectors.OFFER_PRICE_INPUT).first();
        priceInput.waitFor(
                new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(ELEMENT_TIMEOUT_MS)
        );

        String expectedPrice = nextStep.getOfferPrice().toPlainString();
        String actualPrice = priceInput.inputValue();
        if (!expectedPrice.equals(actualPrice)) {
            throw new IllegalStateException(
                    "Prepared next-step form contains an unexpected price. Expected: "
                            + expectedPrice + ", actual: " + actualPrice
            );
        }

        Locator submitButton =
                page.getByTestId(NegotiationSelectors.OFFER_SUBMIT_BUTTON).first();
        submitButton.waitFor(
                new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(ELEMENT_TIMEOUT_MS)
        );

        if (!submitButton.isEnabled()) {
            throw new IllegalStateException(
                    "Prepared next-step submit button is disabled"
            );
        }

        log.info(
                "[NEXT STEP REAL PREPARED] Form verified for listing {}, step {}, price {}. Submit has NOT been clicked.",
                listing.listingId(),
                nextStep.getStepNumber(),
                nextStep.getOfferPrice()
        );
    }

    private String extractConversationId(String conversationUrl) {
        URI uri = URI.create(conversationUrl);
        String path = uri.getPath();
        String[] parts = path == null ? new String[0] : path.split("/");

        for (int i = 0; i < parts.length - 1; i++) {
            if ("inbox".equals(parts[i]) && !parts[i + 1].isBlank()) {
                return parts[i + 1];
            }
        }

        throw new IllegalArgumentException(
                "Cannot extract conversation ID from URL: " + conversationUrl
        );
    }
}
