package pl.flipbot.bot.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RunningBotResponse {

    private Long id;

    private boolean hasActiveNegotiations;

    private boolean captchaRequired;

    private boolean captchaRecoveryRequested;

    private String captchaChallengeUrl;

}
