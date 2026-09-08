package pl.flipbot.bot.runtime.captcha;

import java.time.Instant;

public record CaptchaControlStateResponse(
        Long botId,
        CaptchaControlStatus status,
        Instant updatedAt,
        Instant holdHeartbeatAt,
        String message
) {
}
