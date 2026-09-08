package pl.flipbot.playwright.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RunningBotDto {

    private Long id;

    private boolean hasActiveNegotiations;

    private boolean captchaRequired;

    private boolean captchaRecoveryRequested;

    private String captchaChallengeUrl;

    public boolean hasActiveNegotiations() {
        return hasActiveNegotiations;
    }

    public boolean isCaptchaRequired() {
        return captchaRequired;
    }

    public boolean isCaptchaRecoveryRequested() {
        return captchaRecoveryRequested;
    }

}
