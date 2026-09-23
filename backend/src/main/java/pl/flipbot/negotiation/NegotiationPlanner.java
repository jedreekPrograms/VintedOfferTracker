package pl.flipbot.negotiation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import pl.flipbot.bot.Bot;
import pl.flipbot.bot.configuration.BotConfiguration;
import pl.flipbot.listing.Listing;
import pl.flipbot.listing.ListingRepository;
import pl.flipbot.listing.ListingStatus;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class NegotiationPlanner {

    private final ListingRepository listingRepository;
    private final JdbcTemplate jdbcTemplate;

    public int calculateNewNegotiations(
            Bot bot,
            int remainingDailyActions
    ) {
        BotConfiguration configuration = bot.getConfiguration();
        int mainStepCount = configuration == null
                || configuration.getNegotiationSteps() == null
                ? 0
                : configuration.getNegotiationSteps().size();

        return calculateNewNegotiations(
                bot,
                remainingDailyActions,
                mainStepCount
        );
    }

    public int calculateNewNegotiations(
            Bot bot,
            int remainingDailyActions,
            int stepsRequiredPerNewConversation
    ) {
        if (bot == null
                || bot.getId() == null
                || stepsRequiredPerNewConversation <= 0
                || remainingDailyActions <= 0) {
            return 0;
        }

        int reservedFutureSteps = calculateReservedFutureSteps(
                bot.getId(),
                stepsRequiredPerNewConversation
        );

        int actionsAvailableForNewConversations =
                remainingDailyActions - reservedFutureSteps;

        int allowedNewNegotiations =
                actionsAvailableForNewConversations < stepsRequiredPerNewConversation
                        ? 0
                        : actionsAvailableForNewConversations
                        / stepsRequiredPerNewConversation;

        log.info(
                "[NEGOTIATION CAPACITY] Bot {}: remainingDailyActions={}, reservedFutureStepsAcrossAllProducts={}, stepsRequiredForRequestedProduct={}, actionsLeftAfterReservations={}, allowedNewNegotiations={}.",
                bot.getId(),
                remainingDailyActions,
                reservedFutureSteps,
                stepsRequiredPerNewConversation,
                Math.max(actionsAvailableForNewConversations, 0),
                allowedNewNegotiations
        );

        return allowedNewNegotiations;
    }

    int calculateReservedFutureSteps(
            Long botId,
            int fallbackStepCount
    ) {
        if (botId == null || fallbackStepCount <= 0) {
            return 0;
        }

        List<Listing> activeListings = new ArrayList<>(
                listingRepository.findByBotIdAndStatusOrderByIdAsc(
                        botId,
                        ListingStatus.NEGOTIATING
                )
        );
        activeListings.addAll(
                listingRepository.findByBotIdAndStatusOrderByIdAsc(
                        botId,
                        ListingStatus.ACTION_REQUIRED
                )
        );

        int reservedFutureSteps = 0;
        int supersededActiveDuplicates = 0;

        for (Listing listing : activeListings) {
            if (isSupersededByActiveMarketplaceOwner(listing)) {
                supersededActiveDuplicates++;
                continue;
            }

            int configuredSteps = resolveStepCount(
                    listing,
                    fallbackStepCount
            );
            Integer currentStep = listing.getCurrentStep();

            int remainingSteps = currentStep == null || currentStep < 1
                    ? configuredSteps
                    : Math.max(configuredSteps - currentStep, 0);

            reservedFutureSteps += remainingSteps;

            log.info(
                    "[NEGOTIATION CAPACITY] Bot {} active listing backendId={}, marketplaceId={}, product={}, status={}, currentStep={}, configuredSteps={} reserves {} future action(s).",
                    botId,
                    listing.getId(),
                    listing.getListingId(),
                    listing.getAdditionalTarget() == null
                            ? "MAIN"
                            : listing.getAdditionalTarget().getId(),
                    listing.getStatus(),
                    currentStep,
                    configuredSteps,
                    remainingSteps
            );
        }

        log.info(
                "[NEGOTIATION CAPACITY] Bot {} has {} active row(s), {} superseded duplicate(s), reserving {} future action(s) across all products.",
                botId,
                activeListings.size(),
                supersededActiveDuplicates,
                reservedFutureSteps
        );

        return reservedFutureSteps;
    }

    private int resolveStepCount(
            Listing listing,
            int fallbackStepCount
    ) {
        if (listing != null
                && listing.getAdditionalTarget() != null
                && listing.getAdditionalTarget().getNegotiationSteps() != null
                && !listing.getAdditionalTarget().getNegotiationSteps().isEmpty()) {
            return listing.getAdditionalTarget().getNegotiationSteps().size();
        }

        if (listing != null
                && listing.getBot() != null
                && listing.getBot().getConfiguration() != null
                && listing.getBot().getConfiguration().getNegotiationSteps() != null
                && !listing.getBot().getConfiguration().getNegotiationSteps().isEmpty()) {
            return listing.getBot().getConfiguration().getNegotiationSteps().size();
        }

        log.warn(
                "[NEGOTIATION CAPACITY] Could not resolve product ladder for backend listing {}. Reserving fallback {} step(s) fail-closed.",
                listing == null ? null : listing.getId(),
                fallbackStepCount
        );
        return fallbackStepCount;
    }

    private boolean isSupersededByActiveMarketplaceOwner(
            Listing listing
    ) {
        if (listing == null
                || listing.getId() == null
                || listing.getListingId() == null
                || listing.getBot() == null
                || listing.getBot().getId() == null
                || listing.getBot().getConfiguration() == null
                || listing.getBot().getConfiguration().getMarketplace() == null) {
            return false;
        }

        Boolean superseded = jdbcTemplate.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1
                    FROM marketplace_negotiation_claim c
                    JOIN listing owner
                      ON owner.id = c.owner_listing_id
                    WHERE c.marketplace = ?
                      AND c.marketplace_listing_id = ?
                      AND c.confirmed_at IS NOT NULL
                      AND (
                           c.owner_bot_id <> ?
                           OR c.owner_listing_id <> ?
                      )
                      AND owner.status IN ('NEGOTIATING', 'ACTION_REQUIRED')
                )
                """,
                Boolean.class,
                listing.getBot().getConfiguration().getMarketplace().name(),
                listing.getListingId(),
                listing.getBot().getId(),
                listing.getId()
        );

        return Boolean.TRUE.equals(superseded);
    }
}
