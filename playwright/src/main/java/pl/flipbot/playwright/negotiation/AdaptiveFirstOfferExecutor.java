package pl.flipbot.playwright.negotiation;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.WaitUntilState;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.model.BotConfigurationDto;
import pl.flipbot.playwright.model.NegotiationStepDto;
import pl.flipbot.playwright.target.VintedModelTargetGuard;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Keeps the existing FirstOfferExecutor submit/guard behavior intact, but when
 * Vinted rejects the configured first offer as too low, retries with the
 * adaptive ladder described by BotConfiguration.
 *
 * A missing "Make an offer" action is deliberately confirmed more than once.
 * A single 15-second observation is not enough to permanently classify a
 * historical listing as non-negotiable: Vinted can render item actions late.
 */
@Slf4j
public class AdaptiveFirstOfferExecutor extends FirstOfferExecutor {

    private static final int CANNOT_NEGOTIATE_CONFIRMATION_ATTEMPTS = 3;
    private static final double CANNOT_NEGOTIATE_RETRY_DELAY_MS = 1_500;
    private static final double CANNOT_NEGOTIATE_RELOAD_TIMEOUT_MS = 30_000;
    private static final double STUCK_OFFER_FORM_RESET_TIMEOUT_MS = 30_000;
    private static final double STUCK_OFFER_FORM_SETTLE_MS = 500;

    private static final String ITEM_TITLE_SELECTOR =
            "[data-testid='item-page-summary-plugin'] h1";

    /*
     * Playwright Java serializes Java Pattern flags to JavaScript RegExp flags.
     * UNICODE_CASE is not supported by that bridge and caused the sold-state
     * probe to fail before it could inspect the page. CASE_INSENSITIVE is both
     * sufficient for these two ASCII/Polish labels and Playwright-supported.
     */
    private static final Pattern SOLD_STATUS_TEXT = Pattern.compile(
            "^(Sprzedane|Sold)$",
            Pattern.CASE_INSENSITIVE
    );

    private final BotContext context;
    private final AdaptiveNegotiationPricingService pricingService;
    private final VintedModelTargetGuard modelTargetGuard;

    public AdaptiveFirstOfferExecutor(BotContext context) {
        super(context);
        this.context = context;
        this.pricingService = new AdaptiveNegotiationPricingService();
        this.modelTargetGuard = new VintedModelTargetGuard();
    }

    static Pattern soldStatusPattern() {
        return SOLD_STATUS_TEXT;
    }

    @Override
    public NegotiationPreparationResult prepareFirstOffer(
            ListingResponseDto listing
    ) {
        resetStuckOfferFormBeforeSameListingRetry(listing);

        NegotiationPreparationResult configuredResult =
                prepareWithNegotiationActionConfirmation(listing);

        if (configuredResult != NegotiationPreparationResult.OFFER_TOO_LOW) {
            return applyLiveModelConsistencyGuard(
                    listing,
                    configuredResult
            );
        }

        BotConfigurationDto configuration = context.getBot().getConfiguration();
        if (!pricingService.isAdaptiveModeEnabled(configuration)) {
            return configuredResult;
        }

        NegotiationStepDto firstStep =
                pricingService.firstConfiguredStep(configuration);

        BigDecimal configuredPrice = firstStep.getOfferPrice();

        Optional<BigDecimal> retryPrice =
                pricingService.firstAdaptiveRetryPrice(
                        listing,
                        configuration,
                        configuredPrice
                );

        while (retryPrice.isPresent()) {
            BigDecimal effectivePrice = retryPrice.get();

            /*
             * Vinted sometimes leaves the too-low offer form visible even
             * after Escape. Re-entering FirstOfferExecutor in that state used
             * to fail with "Offer form was already visible" and waste the
             * whole candidate/run. No submit or quota happened yet, so a page
             * reload is a safe way to restore a clean item-page state.
             */
            resetStuckOfferFormBeforeSameListingRetry(listing);

            log.warn(
                    "[ADAPTIVE FIRST OFFER] Retrying marketplace listing {} with adaptive first offer {} instead of configured {}. Global negotiation cap={}.",
                    listing.listingId(),
                    effectivePrice,
                    configuredPrice,
                    configuration.getMaxAutomaticOffer()
            );

            NegotiationPreparationResult retryResult;

            firstStep.setOfferPrice(effectivePrice);
            try {
                retryResult = prepareWithNegotiationActionConfirmation(listing);
            } finally {
                /*
                 * The prepared offer stored inside FirstOfferExecutor already
                 * captured the effective price. Restore the shared bot DTO so
                 * other listings still start from the configured ladder.
                 */
                firstStep.setOfferPrice(configuredPrice);
            }

            if (retryResult != NegotiationPreparationResult.OFFER_TOO_LOW) {
                return applyLiveModelConsistencyGuard(
                        listing,
                        retryResult
                );
            }

            retryPrice = pricingService.nextFirstOfferRetry(
                    effectivePrice,
                    configuration
            );
        }

        log.info(
                "[ADAPTIVE FIRST OFFER] Marketplace listing {} cannot be negotiated automatically because every acceptable first-offer retry would exceed the global negotiation cap {}. No quota was reserved and no offer was sent.",
                listing.listingId(),
                configuration.getMaxAutomaticOffer()
        );

        return NegotiationPreparationResult.OFFER_TOO_LOW;
    }

    /**
     * Historical DISCOVERED records do not carry proof that they were created
     * by today's exact Vinted model filter. Therefore, immediately before a
     * prepared real offer can reach quota/submit, compare the already-loaded
     * item-page title with the configured model and reject only CONCLUSIVE
     * conflicts. Generic seller titles remain allowed.
     */
    private NegotiationPreparationResult applyLiveModelConsistencyGuard(
            ListingResponseDto listing,
            NegotiationPreparationResult result
    ) {
        if (result != NegotiationPreparationResult.PREPARED) {
            return result;
        }

        BotConfigurationDto configuration = context.getBot().getConfiguration();
        if (!usesVintedModelFilter(configuration)) {
            return result;
        }

        String visibleTitle = readVisibleItemTitle();
        if (visibleTitle == null || visibleTitle.isBlank()) {
            cancelPreparedOfferSafely();
            throw new IllegalStateException(
                    "Prepared VINTED_MODEL offer cannot pass final live target consistency because the visible item h1 disappeared before quota reservation. Marketplace listing: "
                            + listing.listingId()
            );
        }

        Optional<String> mismatch = modelTargetGuard.findConclusiveMismatch(
                configuration.getModel(),
                visibleTitle
        );

        if (mismatch.isPresent()) {
            log.error(
                    "[LIVE TARGET GUARD] Marketplace listing {} is a conclusive wrong-model match for configured Vinted model '{}'. Visible h1='{}'. Reason: {}. Prepared form will be cancelled; no quota or offer will be used.",
                    listing.listingId(),
                    configuration.getModel(),
                    visibleTitle,
                    mismatch.get()
            );

            cancelPreparedOfferSafely();
            resetStuckOfferFormBeforeSameListingRetry(listing);
            return NegotiationPreparationResult.TARGET_MISMATCH;
        }

        log.info(
                "[LIVE TARGET GUARD] Marketplace listing {} passed final live model consistency for configured '{}'. Visible h1='{}'.",
                listing.listingId(),
                configuration.getModel(),
                visibleTitle
        );

        return result;
    }

    private boolean usesVintedModelFilter(BotConfigurationDto configuration) {
        if (configuration == null) {
            return false;
        }

        String targetMode = configuration.getTargetMode();
        return targetMode == null
                || targetMode.isBlank()
                || "VINTED_MODEL".equalsIgnoreCase(targetMode.trim());
    }

    private String readVisibleItemTitle() {
        try {
            Locator title = context.getPage()
                    .locator(ITEM_TITLE_SELECTOR)
                    .first();

            if (!title.isVisible()) {
                return null;
            }

            String value = title.innerText();
            if (value == null) {
                return null;
            }

            return value.trim().replaceAll("\\s+", " ");
        } catch (PlaywrightException exception) {
            log.debug(
                    "[LIVE TARGET GUARD] Could not read the current item title.",
                    exception
            );
            return null;
        }
    }

    private void resetStuckOfferFormBeforeSameListingRetry(
            ListingResponseDto listing
    ) {
        if (listing == null
                || listing.listingId() == null
                || listing.listingId().isBlank()) {
            return;
        }

        Page page = context.getPage();
        if (page == null || page.isClosed()) {
            return;
        }

        String currentUrl = page.url();
        if (currentUrl == null
                || !currentUrl.contains("/items/" + listing.listingId())) {
            return;
        }

        Locator priceInput = page.getByTestId(
                        NegotiationSelectors.OFFER_PRICE_INPUT
                )
                .first();

        boolean visible;
        try {
            visible = priceInput.isVisible();
        } catch (PlaywrightException exception) {
            visible = false;
        }

        if (!visible) {
            return;
        }

        log.warn(
                "[ADAPTIVE FIRST OFFER] Offer form is still visible for marketplace listing {} before a safe pre-submit retry. Reloading the item page to clear stale modal state. No quota has been reserved and no submit was attempted.",
                listing.listingId()
        );

        page.reload(
                new Page.ReloadOptions()
                        .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout(STUCK_OFFER_FORM_RESET_TIMEOUT_MS)
        );
        page.waitForTimeout(STUCK_OFFER_FORM_SETTLE_MS);

        try {
            if (priceInput.isVisible()) {
                throw new IllegalStateException(
                        "Offer form remained visible after safe item-page reset for marketplace listing "
                                + listing.listingId()
                );
            }
        } catch (PlaywrightException ignored) {
            /* DOM was replaced by reload, which is the desired outcome. */
        }
    }

    private NegotiationPreparationResult prepareWithNegotiationActionConfirmation(
            ListingResponseDto listing
    ) {
        for (int attempt = 1;
             attempt <= CANNOT_NEGOTIATE_CONFIRMATION_ATTEMPTS;
             attempt++) {

            NegotiationPreparationResult result = super.prepareFirstOffer(listing);

            if (result != NegotiationPreparationResult.CANNOT_NEGOTIATE) {
                return result;
            }

            /*
             * Vinted's sold item page can still expose a perfectly valid h1,
             * so the generic item-page availability check may see a loaded
             * listing while the offer action is intentionally absent. The
             * visible green "Sprzedane"/"Sold" status is authoritative enough
             * to classify the listing as UNAVAILABLE instead of permanently
             * labelling it CANNOT_NEGOTIATE.
             */
            if (isExplicitlySold(context.getPage())) {
                log.info(
                        "[REAL OFFER AVAILABILITY] Marketplace listing {} is explicitly marked as sold by Vinted. Returning LISTING_UNAVAILABLE; caller will persist UNAVAILABLE and no quota/offer will be used.",
                        listing.listingId()
                );
                return NegotiationPreparationResult.LISTING_UNAVAILABLE;
            }

            if (attempt >= CANNOT_NEGOTIATE_CONFIRMATION_ATTEMPTS) {
                log.warn(
                        "[REAL OFFER AVAILABILITY] Marketplace listing {} exposed no offer action in {}/{} fully loaded checks. Each check already waited up to the normal 15-second button timeout. Only now is CANNOT_NEGOTIATE considered confirmed for this run; this does not claim the seller blocked the account, only that Vinted repeatedly exposed no negotiation action.",
                        listing.listingId(),
                        attempt,
                        CANNOT_NEGOTIATE_CONFIRMATION_ATTEMPTS
                );
                return result;
            }

            log.warn(
                    "[REAL OFFER AVAILABILITY] Marketplace listing {} exposed no offer action on observation {}/{}. This is NOT enough to persist CANNOT_NEGOTIATE. Waiting briefly, reloading the item page and checking again before any permanent skip.",
                    listing.listingId(),
                    attempt,
                    CANNOT_NEGOTIATE_CONFIRMATION_ATTEMPTS
            );

            Page page = context.getPage();
            page.waitForTimeout(CANNOT_NEGOTIATE_RETRY_DELAY_MS);
            page.reload(
                    new Page.ReloadOptions()
                            .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                            .setTimeout(CANNOT_NEGOTIATE_RELOAD_TIMEOUT_MS)
            );
            page.waitForTimeout(CANNOT_NEGOTIATE_RETRY_DELAY_MS);
        }

        throw new IllegalStateException(
                "Negotiation-action confirmation loop exited unexpectedly"
        );
    }

    private boolean isExplicitlySold(Page page) {
        try {
            Locator exactSoldLabels = page.getByText(
                    SOLD_STATUS_TEXT,
                    new Page.GetByTextOptions().setExact(true)
            );

            int count = Math.min(exactSoldLabels.count(), 20);
            for (int index = 0; index < count; index++) {
                if (exactSoldLabels.nth(index).isVisible()) {
                    return true;
                }
            }

            String bodyText = page.locator("body").innerText();
            if (bodyText == null || bodyText.isBlank()) {
                return false;
            }

            String normalized = bodyText
                    .replaceAll("\\s+", " ")
                    .toLowerCase();

            return normalized.contains("przedmiot został sprzedany")
                    || normalized.contains("przedmiot zostal sprzedany")
                    || normalized.contains("item has been sold")
                    || normalized.contains("item was sold");

        } catch (PlaywrightException exception) {
            log.debug(
                    "[REAL OFFER AVAILABILITY] Sold-state probe could not read the current item page; normal availability safeguards remain active.",
                    exception
            );
            return false;
        }
    }
}
