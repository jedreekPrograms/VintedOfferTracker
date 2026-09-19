package pl.flipbot.playwright.session;

import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.context.BotContext;

/** Finalizes a job without replacing its current authenticated checkpoint with an older backup. */
@Slf4j
public final class JobSessionPersistence {

    private final VintedSessionPersistenceGuard guard = new VintedSessionPersistenceGuard();

    public void finish(
            BotContext context,
            boolean loginReady,
            boolean jobCompleted,
            boolean authenticatedCheckpointReady
    ) {
        if (!loginReady) {
            return;
        }

        Long botId = context.getBot().getId();
        if (!jobCompleted) {
            log.warn(
                    "[SESSION] Bot {} job did not complete successfully. Keeping the current authenticated checkpoint; browser state will not replace it.",
                    botId
            );
            return;
        }

        try {
            if (authenticatedCheckpointReady) {
                VintedSessionPersistenceGuard.Check check = guard.check(context);
                if (!check.healthy()) {
                    // saveSession before the job installed the fresh checkpoint
                    // as bot-X.json. The rotating backup is the PREVIOUS file,
                    // not that checkpoint, and may contain older refresh tokens.
                    log.warn(
                            "[SESSION] Bot {} final browser state is not safe to persist. Keeping the current authenticated pre-job checkpoint in bot-{}.json; no older backup is restored. reason={}",
                            botId,
                            botId,
                            check.reason()
                    );
                    return;
                }
            }

            context.saveSession();
        } catch (RuntimeException exception) {
            log.warn(
                    "[SESSION] Could not verify/save the final session for bot {}. The current checkpoint is retained and browser cleanup will continue.",
                    botId,
                    exception
            );
        }
    }
}
