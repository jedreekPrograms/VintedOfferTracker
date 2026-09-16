package pl.flipbot.playwright.worker;

import pl.flipbot.playwright.model.RunningBotDto;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Immutable snapshots written by the scheduler-sync thread and read by worker
 * threads. The registry only controls whether a normal scheduled job is headed;
 * it never creates a separate preview browser or owns authentication state.
 */
final class BotSessionPreviewRegistry {

    private volatile Set<Long> previewRequestedBotIds = Set.of();

    void replaceFrom(List<RunningBotDto> runningBots) {
        if (runningBots == null || runningBots.isEmpty()) {
            previewRequestedBotIds = Set.of();
            return;
        }

        previewRequestedBotIds = runningBots.stream()
                .filter(bot -> bot != null && bot.getId() != null && bot.getId() > 0L)
                .filter(RunningBotDto::isSessionPreviewRequested)
                .map(RunningBotDto::getId)
                .collect(Collectors.toUnmodifiableSet());
    }

    boolean isPreviewRequested(Long botId) {
        return botId != null && previewRequestedBotIds.contains(botId);
    }

    void clear() {
        previewRequestedBotIds = Set.of();
    }
}
