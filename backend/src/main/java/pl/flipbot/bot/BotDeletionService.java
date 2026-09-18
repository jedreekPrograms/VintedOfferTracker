package pl.flipbot.bot;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import pl.flipbot.exception.BotNotFoundException;
import pl.flipbot.listing.ListingRepository;
import pl.flipbot.listing.ListingStatus;
import pl.flipbot.negotiation.guard.RealActionGuardRepository;
import pl.flipbot.bot.runtime.BotRuntimeStateRepository;
import pl.flipbot.bot.runtime.BotRuntimeStatus;

@Service
@RequiredArgsConstructor
public class BotDeletionService {

    private final BotRepository botRepository;

    private final ListingRepository listingRepository;

    private final RealActionGuardRepository realActionGuardRepository;

    private final BotRuntimeStateRepository runtimeStateRepository;

    private final JdbcTemplate jdbcTemplate;


    @Transactional
    public void deleteBot(
            Long botId
    ) {

        Bot bot =
                botRepository.findById(
                                botId
                        )
                        .orElseThrow(
                                () ->
                                        new BotNotFoundException(
                                                botId
                                        )
                        );


        if (
                bot.getStatus()
                        != BotStatus.STOPPED
        ) {

            throw new IllegalStateException(
                    "Only a stopped bot can be deleted."
            );
        }


        ensureBotIsNotWorking(
                botId
        );

        ensureBotHasNoActiveListings(
                botId
        );

        reconcileConfirmedMarketplaceClaims(
                botId
        );

        ensureBotHasNoUnresolvedRealAction(
                botId
        );


        botRepository.delete(
                bot
        );
    }


    private void ensureBotIsNotWorking(
            Long botId
    ) {
        boolean working = runtimeStateRepository.findById(botId)
                .map(state -> state.getRuntimeStatus() == BotRuntimeStatus.WORKING)
                .orElse(false);

        if (working) {
            throw new IllegalStateException(
                    "Bot cannot be deleted while its Playwright worker is still finishing a job. "
                            + "Wait until Runtime no longer shows WORKING and try again."
            );
        }
    }


    private void reconcileConfirmedMarketplaceClaims(
            Long botId
    ) {
        int confirmed = jdbcTemplate.update(
                """
                UPDATE marketplace_negotiation_claim AS claim
                SET confirmed_at = COALESCE(claim.confirmed_at, CURRENT_TIMESTAMP)
                WHERE claim.owner_bot_id = ?
                  AND claim.confirmed_at IS NULL
                  AND EXISTS (
                      SELECT 1
                      FROM listing
                      WHERE listing.id = claim.owner_listing_id
                        AND listing.bot_id = ?
                        AND COALESCE(listing.current_step, 0) >= 1
                  )
                """,
                botId,
                botId
        );

        if (confirmed > 0) {
            /*
             * current_step >= 1 is durable backend proof that FIRST_OFFER
             * completed far enough to start the negotiation. Preserve global
             * ownership before the bot/listing rows are deleted.
             */
        }
    }


    private void ensureBotHasNoUnresolvedRealAction(
            Long botId
    ) {
        long unresolvedGuards =
                realActionGuardRepository.countUnresolvedByBotId(
                        botId
                );

        Boolean hasUnconfirmedMarketplaceClaim =
                jdbcTemplate.queryForObject(
                        """
                        SELECT EXISTS (
                            SELECT 1
                            FROM marketplace_negotiation_claim
                            WHERE owner_bot_id = ?
                              AND confirmed_at IS NULL
                        )
                        """,
                        Boolean.class,
                        botId
                );

        if (unresolvedGuards > 0L
                || Boolean.TRUE.equals(hasUnconfirmedMarketplaceClaim)) {
            throw new IllegalStateException(
                    "Bot cannot be deleted while a real marketplace action is unresolved. "
                            + "Restart/check the bot so the persistent action guard can be reconciled safely."
            );
        }
    }


    private void ensureBotHasNoActiveListings(
            Long botId
    ) {

        boolean hasNegotiatingListings =
                !listingRepository
                        .findByBotIdAndStatusOrderByIdAsc(
                                botId,
                                ListingStatus.NEGOTIATING
                        )
                        .isEmpty();


        boolean hasActionRequiredListings =
                !listingRepository
                        .findByBotIdAndStatusOrderByIdAsc(
                                botId,
                                ListingStatus.ACTION_REQUIRED
                        )
                        .isEmpty();


        if (
                hasNegotiatingListings
                        || hasActionRequiredListings
        ) {

            throw new IllegalStateException(
                    "Bot cannot be deleted while it has active negotiations "
                            + "or listings requiring user action."
            );
        }
    }
}
