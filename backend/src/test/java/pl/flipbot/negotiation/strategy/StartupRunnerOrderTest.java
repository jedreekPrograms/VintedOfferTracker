package pl.flipbot.negotiation.strategy;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.Order;
import pl.flipbot.bot.configuration.BotAdditionalTargetSchemaInitializer;
import pl.flipbot.listing.HistoryAnalyticsSchemaInitializer;
import pl.flipbot.listing.TerminalNegotiationWatchSchemaInitializer;
import pl.flipbot.marketstats.MarketStatsPublicationSchemaInitializer;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StartupRunnerOrderTest {

    @Test
    void listingJpaBackfillRunsAfterAllCurrentSchemaInitializers() {
        int backfillOrder = orderOf(
                NegotiationStrategySnapshotBackfillRunner.class
        );

        assertTrue(
                backfillOrder > orderOf(
                        BotAdditionalTargetSchemaInitializer.class
                )
        );
        assertTrue(
                backfillOrder > orderOf(
                        NegotiationStrategySchemaInitializer.class
                )
        );
        assertTrue(
                backfillOrder > orderOf(
                        TerminalNegotiationWatchSchemaInitializer.class
                )
        );
        assertTrue(
                backfillOrder > orderOf(
                        HistoryAnalyticsSchemaInitializer.class
                )
        );
        assertTrue(
                backfillOrder > orderOf(
                        MarketStatsPublicationSchemaInitializer.class
                )
        );
    }

    private int orderOf(Class<?> type) {
        Order order = type.getAnnotation(Order.class);
        if (order == null) {
            throw new AssertionError(
                    type.getSimpleName() + " must declare @Order"
            );
        }
        return order.value();
    }
}
