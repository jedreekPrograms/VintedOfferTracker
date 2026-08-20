package pl.flipbot.playwright.negotiation;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ConsecutiveContactUnavailableTracker {

    static final int REQUIRED_CONSECUTIVE_SUSPECTED_CHECKS = 3;

    private static final ConcurrentMap<Key, Integer> COUNTS =
            new ConcurrentHashMap<>();

    public int recordSuspected(
            Long botId,
            String marketplaceListingId
    ) {
        Key key = key(botId, marketplaceListingId);
        return COUNTS.merge(key, 1, Integer::sum);
    }

    public boolean shouldClose(int consecutiveChecks) {
        return consecutiveChecks >= REQUIRED_CONSECUTIVE_SUSPECTED_CHECKS;
    }

    public void clear(
            Long botId,
            String marketplaceListingId
    ) {
        COUNTS.remove(key(botId, marketplaceListingId));
    }

    static void clearAllForTests() {
        COUNTS.clear();
    }

    private Key key(
            Long botId,
            String marketplaceListingId
    ) {
        if (botId == null || botId <= 0) {
            throw new IllegalArgumentException("Bot ID must be positive");
        }

        if (marketplaceListingId == null
                || marketplaceListingId.isBlank()) {
            throw new IllegalArgumentException(
                    "Marketplace listing ID is required"
            );
        }

        return new Key(
                botId,
                marketplaceListingId.trim()
        );
    }

    private record Key(
            Long botId,
            String marketplaceListingId
    ) {
        private Key {
            Objects.requireNonNull(botId);
            Objects.requireNonNull(marketplaceListingId);
        }
    }
}
