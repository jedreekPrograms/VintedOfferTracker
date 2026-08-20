package pl.flipbot.playwright.negotiation;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitUntilState;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.model.BotConfigurationDto;
import pl.flipbot.playwright.model.NegotiationStepDto;

import java.math.BigDecimal;
import java.util.Optional;

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

    private final BotContext context;
    private final AdaptiveNegotiationPricingService pricingService;

    public AdaptiveFirstOfferExecutor(BotContext context) {
        super(context);
        this.context = context;
        this.pricingService = new AdaptiveNegotiationPricingService();
    }

    @Override
    public NegotiationPreparationResult prepareFirstOffer(
            ListingResponseDto listing
    ) {
        NegotiationPreparationResult configuredResult =
                prepareWithNegotiationActionConfirmation(listing);

        if (configuredResult != NegotiationPreparationResult.OFFER_TOO_LOW) {
            return configuredResult;
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
                return retryResult;
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
}
