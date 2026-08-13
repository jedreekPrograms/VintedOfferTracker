package pl.flipbot.bot.runtime;

public enum RuntimeEventType {
    QUEUED,
    RUN_STARTED,
    RUN_SUCCEEDED,
    RUN_FAILED,
    RATE_LIMITED,
    IDLE
}
