package pl.flipbot.playwright.worker;

import pl.flipbot.playwright.negotiation.ConsecutiveContactUnavailableTracker;
import pl.flipbot.playwright.negotiation.MultiProductExistingNegotiationProcessor;
import pl.flipbot.playwright.processing.CatalogWorkProcessor;

/**
 * Process-local state that is useful only while a bot belongs to the RUNNING
 * scheduler set. It is safe to discard once that bot's schedule is fully
 * removed.
 *
 * <p>Deliberately does NOT clear BotRunExecutor's process-wide one-shot real
 * action safety sets. Those must survive STOP/START and are armed until the
 * Playwright JVM itself restarts.</p>
 */
final class BotEphemeralRuntimeState {

    private BotEphemeralRuntimeState() {
    }

    static void clearForBot(Long botId) {
        if (botId == null) {
            return;
        }

        CatalogWorkProcessor.clearRotationState(botId);
        MultiProductExistingNegotiationProcessor.clearRotationState(botId);
        ConsecutiveContactUnavailableTracker.clearBot(botId);
    }
}
