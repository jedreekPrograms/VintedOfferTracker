package pl.flipbot.bot;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import pl.flipbot.exception.BotNotFoundException;
import pl.flipbot.listing.ListingRepository;
import pl.flipbot.listing.ListingStatus;
import pl.flipbot.negotiation.guard.RealActionGuardRepository;

@Service
@RequiredArgsConstructor
public class BotDeletionService {

    private final BotRepository botRepository;

    private final ListingRepository listingRepository;

    private final RealActionGuardRepository realActionGuardRepository;

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


        ensureBotHasNoActiveListings(
                botId
        );

        ensureBotHasNoUnresolvedRealActions(
                botId
        );


        botRepository.delete(
                bot
        );
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


    private void ensureBotHasNoUnresolvedRealActions(
            Long botId
    ) {
        boolean hasActiveGuard =
                realActionGuardRepository.existsByListing_Bot_Id(botId);

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

        if (hasActiveGuard || Boolean.TRUE.equals(hasUnconfirmedMarketplaceClaim)) {
            throw new IllegalStateException(
                    "Bot cannot be deleted while a real action is unresolved. "
                            + "Resolve or reconcile the persistent action guard/marketplace claim first."
            );
        }
    }
}
