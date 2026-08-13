package pl.flipbot.playwright.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RunningBotDto {

    private Long id;

    private boolean hasActiveNegotiations;

    public boolean hasActiveNegotiations() {
        return hasActiveNegotiations;
    }

}
