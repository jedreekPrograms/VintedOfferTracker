package pl.flipbot.playwright.negotiation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.api.quota.OfferQuotaClient;
import pl.flipbot.playwright.api.quota.dto.OfferQuotaReservationResponseDto;
import pl.flipbot.playwright.context.BotContext;

import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class PreparedNextStepCoordinator {

    private static final String ACTION_TYPE = "NEXT_STEP";

    private final BotContext context;
    private final OfferQuotaClient offerQuotaClient;

    public boolean execute(
            ListingResponseDto listing,
            NegotiationDecision decision
    ) {
        if (decision.nextStep() == null) {
            throw new IllegalStateException("Missing next negotiation step");
        }

        NextNegotiationStepExecutor preparationExecutor =
                new NextNegotiationStepExecutor(context);
        NextStepPreparationResult preparation =
                preparationExecutor.prepareDryRun(listing, decision.nextStep());

        if (preparation == NextStepPreparationResult.OFFER_TOO_LOW) {
            return false;
        }

        Long botId = context.getBot().getId();
        NextStepActionGuardCoordinator guard =
                new NextStepActionGuardCoordinator();
        RealActionAuditCoordinator audit =
                new RealActionAuditCoordinator(context);
        UUID requestId = guard.acquire(botId, listing, decision.nextStep());

        if (requestId == null) {
            return false;
        }

        OfferQuotaReservationResponseDto quota;
        try {
            quota = offerQuotaClient.reserveSlot(botId, requestId);
        } catch (Exception exception) {
            guard.releaseBeforeSubmitOrRetrySafely(
                    botId,
                    listing,
                    requestId,
                    "quota reservation failed before final action"
            );
            throw exception;
        }

        if (!quota.reserved()) {
            guard.releaseBeforeSubmitOrRetrySafely(
                    botId,
                    listing,
                    requestId,
                    "daily quota not reserved"
            );
            return false;
        }

        try {
            new PreparedNextStepStateVerifier(context)
                    .verify(listing, decision.nextStep());
        } catch (Exception exception) {
            releaseQuota(botId, requestId);
            guard.releaseBeforeSubmitOrRetrySafely(
                    botId,
                    listing,
                    requestId,
                    "prepared state changed before final action"
            );
            throw exception;
        }

        NextStepExecutionResult result;
        try {
            result = new PreparedNextStepSubmitter(context)
                    .submitPrepared(listing, decision.nextStep());
        } catch (Exception exception) {
            audit.recordAmbiguousBestEffort(
                    listing,
                    ACTION_TYPE,
                    decision.nextStep().getStepNumber(),
                    requestId,
                    exception
            );

            log.error(
                    "Prepared next-step finalization failed for listing {}. State is kept fail-closed.",
                    listing.listingId()
            );
            throw exception;
        }

        if (result != NextStepExecutionResult.SENT) {
            IllegalStateException exception = new IllegalStateException(
                    "Unexpected next-step result: " + result
            );

            audit.recordAmbiguousBestEffort(
                    listing,
                    ACTION_TYPE,
                    decision.nextStep().getStepNumber(),
                    requestId,
                    exception
            );
            throw exception;
        }

        audit.recordConfirmedRequired(
                listing,
                ACTION_TYPE,
                decision.nextStep().getStepNumber(),
                requestId
        );

        guard.releaseAfterConfirmedSuccessBestEffort(
                botId,
                listing,
                requestId
        );
        return true;
    }

    private void releaseQuota(
            Long botId,
            UUID requestId
    ) {
        try {
            offerQuotaClient.releaseSlot(botId, requestId);
        } catch (Exception exception) {
            log.error(
                    "Could not release quota slot for bot {} requestId={}",
                    botId,
                    requestId,
                    exception
            );
        }
    }
}
