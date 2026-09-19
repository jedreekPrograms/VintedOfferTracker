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
import pl.flipbot.playwright.target.VintedItemIdentityReader;
import pl.flipbot.playwright.target.ListingTargetMatcher;

import java.math.BigDecimal;


import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Keeps FirstOfferExecutor's submit/guard behavior intact and adds adaptive
 * first-offer pricing plus the last live identity gate before quota/submit.
 */
@Slf4j
public class AdaptiveFirstOfferExecutor extends FirstOfferExecutor {

    private static final int CANNOT_NEGOTIATE_CONFIRMATION_ATTEMPTS = 3;
    private static final double CANNOT_NEGOTIATE_RETRY_DELAY_MS = 1_500;
    private static final double CANNOT_NEGOTIATE_RELOAD_TIMEOUT_MS = 30_000;
    private static final double STUCK_OFFER_FORM_RESET_TIMEOUT_MS = 30_000;
    private static final double STUCK_OFFER_FORM_SETTLE_MS = 500;

    private static final Pattern SOLD_STATUS_TEXT = Pattern.compile(
            "^(Sprzedane|Sold)$",
            Pattern.CASE_INSENSITIVE
    );

    private final BotContext context;
    private final AdaptiveNegotiationPricingService pricingService;
    private final ListingTargetMatcher listingTargetMatcher;
    private final VintedItemIdentityReader itemIdentityReader;

    public AdaptiveFirstOfferExecutor(BotContext context) {
        super(context);
        this.context = context;
        this.pricingService = new AdaptiveNegotiationPricingService();
        this.listingTargetMatcher = new ListingTargetMatcher();
        this.itemIdentityReader = new VintedItemIdentityReader();
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
            return applyLiveTargetConsistencyGuard(
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
                firstStep.setOfferPrice(configuredPrice);
            }

            if (retryResult != NegotiationPreparationResult.OFFER_TOO_LOW) {
                return applyLiveTargetConsistencyGuard(
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
     * Live target guard applies ONLY to SEARCH_QUERY mode.
     *
     * VINTED_MODEL is intentionally different: the native Vinted model filter
     * is the authoritative source of target identity. Once a listing came from
     * the current exact filtered result set, do not re-judge it from seller h1
     * text or a sometimes-missing structured Model field.
     */
    private NegotiationPreparationResult applyLiveTargetConsistencyGuard(
            ListingResponseDto listing,
            NegotiationPreparationResult result
    ) {
        if (result != NegotiationPreparationResult.PREPARED) {
            return result;
        }

        BotConfigurationDto configuration = context.getBot().getConfiguration();

        if (usesVintedModelFilter(configuration)) {
            log.debug(
                    "[LIVE TARGET GUARD] Skipping item-page identity guard for marketplace listing {} because targetMode=VINTED_MODEL trusts the native Vinted model filter.",
                    listing.listingId()
            );
            return result;
        }

        if (!usesSearchQuery(configuration)) {
            return result;
        }

        VintedItemIdentityReader.ItemIdentity identity =
                itemIdentityReader.read(context.getPage());

        String visibleTitle = identity.title();
        if (visibleTitle == null || visibleTitle.isBlank()) {
            cancelPreparedOfferSafely();
            throw new IllegalStateException(
                    "Prepared SEARCH_QUERY offer has no readable live item title for marketplace listing "
                            + listing.listingId()
                            + ". Failing closed before quota reservation."
            );
        }

        if (!listingTargetMatcher.matchesFullTitle(
                visibleTitle,
                configuration
        )) {
            log.error(
                    "[LIVE TARGET GUARD] Marketplace listing {} does not match SEARCH_QUERY '{}'. h1='{}'. Prepared form will be cancelled; no quota or offer will be used.",
                    listing.listingId(),
                    configuration.getSearchQuery(),
                    visibleTitle
            );

            cancelPreparedOfferSafely();
            resetStuckOfferFormBeforeSameListingRetry(listing);
            return NegotiationPreparationResult.TARGET_MISMATCH;
        }

        log.info(
                "[LIVE TARGET GUARD] Marketplace listing {} passed SEARCH_QUERY live title verification. query='{}', h1='{}'.",
                listing.listingId(),
                configuration.getSearchQuery(),
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

    private boolean usesSearchQuery(BotConfigurationDto configuration) {
        return configuration != null
                && configuration.getTargetMode() != null
                && "SEARCH_QUERY".equalsIgnoreCase(
                configuration.getTargetMode().trim()
        );
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
            // DOM replacement after reload is the desired outcome.
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
                    .toLowerCase(Locale.ROOT);

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
