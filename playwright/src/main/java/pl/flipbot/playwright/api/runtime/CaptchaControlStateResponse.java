package pl.flipbot.playwright.api.runtime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CaptchaControlStateResponse(
        Long botId,
        String status,
        String updatedAt,
        String holdHeartbeatAt,
        String message
) {
    public boolean holding() {
        return "HOLDING".equals(status);
    }

    public boolean ready() {
        return "READY".equals(status) || holding();
    }
}
