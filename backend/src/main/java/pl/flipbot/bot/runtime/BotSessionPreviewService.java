package pl.flipbot.bot.runtime;

import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Process-local request to show one bot's normal Playwright jobs in a headed
 * browser. This is intentionally not persisted: restarting the backend returns
 * every bot to the normal global headless policy and never changes a stored
 * authentication session.
 */
@Service
public class BotSessionPreviewService {

    private final Set<Long> previewRequestedBotIds =
            ConcurrentHashMap.newKeySet();

    public boolean setPreviewRequested(Long botId, boolean requested) {
        validateBotId(botId);

        if (requested) {
            previewRequestedBotIds.add(botId);
        } else {
            previewRequestedBotIds.remove(botId);
        }

        return requested;
    }

    public boolean isPreviewRequested(Long botId) {
        return botId != null && previewRequestedBotIds.contains(botId);
    }

    public void retainRunningBots(Collection<Long> runningBotIds) {
        Set<Long> normalizedRunningBotIds = runningBotIds == null
                ? Set.of()
                : new HashSet<>(runningBotIds);

        previewRequestedBotIds.retainAll(normalizedRunningBotIds);
    }

    private void validateBotId(Long botId) {
        if (botId == null || botId <= 0L) {
            throw new IllegalArgumentException("Bot ID must be positive.");
        }
    }
}
