package pl.flipbot.playwright.negotiation;

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
 */
@Slf4j
public class AdaptiveFirstOfferExecutor extends FirstOfferExecutor {

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
                super.prepareFirstOffer(listing);

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
                retryResult = super.prepareFirstOffer(listing);
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
}
