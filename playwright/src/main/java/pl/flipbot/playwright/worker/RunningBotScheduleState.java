package pl.flipbot.playwright.worker;

/**
 * Backend-owned scheduler state for one RUNNING bot.
 */
public record RunningBotScheduleState(
        boolean hasActiveNegotiations,
        boolean captchaRequired,
        boolean captchaRecoveryRequested
) {
}
