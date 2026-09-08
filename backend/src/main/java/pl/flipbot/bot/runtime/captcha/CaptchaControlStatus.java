package pl.flipbot.bot.runtime.captcha;

public enum CaptchaControlStatus {
    IDLE,
    PREPARING,
    READY,
    HOLDING,
    COMPLETED,
    FAILED
}
