package pl.flipbot.bot.runtime;

public enum RuntimeEventType {
    QUEUED,
    RUN_STARTED,
    RUN_SUCCEEDED,
    RUN_FAILED,
    RATE_LIMITED,
    SESSION_BLOCKED,
    CAPTCHA_REQUIRED,
    CAPTCHA_RECOVERY_STARTED,
    CAPTCHA_RECOVERY_SUCCEEDED,
    IDLE
}
