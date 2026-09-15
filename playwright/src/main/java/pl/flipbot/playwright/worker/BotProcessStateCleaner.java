package pl.flipbot.playwright.worker;

import pl.flipbot.playwright.negotiation.ConsecutiveContactUnavailableTracker;
import pl.flipbot.playwright.negotiation.MultiProductExistingNegotiationProcessor;
import pl.flipbot.playwright.processing.CatalogWorkProcessor;

final class BotProcessStateCleaner {

    private BotProcessStateCleaner() {
    }

    static void clear(Long botId) {
        if (botId == null) {
            return;
        }

        CatalogWorkProcessor.clearProcessState(botId);
        MultiProductExistingNegotiationProcessor.clearProcessState(botId);
        ConsecutiveContactUnavailableTracker.clearProcessState(botId);
    }
}
