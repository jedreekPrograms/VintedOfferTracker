package pl.flipbot.playwright.api.quota;

import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.quota.dto.OfferQuotaReservationResponseDto;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Enforces the configured Playwright per-catalog-run action cap across all
 * products of one bot. Backend daily quota remains authoritative as well.
 */
@Slf4j
public class RunScopedOfferQuotaClient extends OfferQuotaClient {

    private final OfferQuotaClient delegate;
    private final int maxReservations;
    private final Set<UUID> countedReservations = new HashSet<>();

    public RunScopedOfferQuotaClient(
            OfferQuotaClient delegate,
            int maxReservations
    ) {
        this.delegate = delegate;
        this.maxReservations = Math.max(maxReservations, 0);
    }

    @Override
    public OfferQuotaReservationResponseDto reserveSlot(
            Long botId,
            UUID requestId
    ) {
        if (countedReservations.size() >= maxReservations) {
            log.info(
                    "[ACTION LIMIT] Bot {} already used all {} real first-offer slots for this multi-product catalog run.",
                    botId,
                    maxReservations
            );
            return new OfferQuotaReservationResponseDto(
                    false,
                    maxReservations,
                    maxReservations,
                    0
            );
        }

        OfferQuotaReservationResponseDto result = delegate.reserveSlot(
                botId,
                requestId
        );
        if (result.reserved()) {
            countedReservations.add(requestId);
        }
        return result;
    }

    @Override
    public void releaseSlot(Long botId, UUID requestId) {
        delegate.releaseSlot(botId, requestId);
        countedReservations.remove(requestId);
    }
}
