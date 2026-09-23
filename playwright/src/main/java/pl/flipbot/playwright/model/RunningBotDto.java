package pl.flipbot.playwright.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RunningBotDto {

    private Long id;

    private boolean hasActiveNegotiations;

    private boolean sessionPreviewRequested;

    public boolean hasActiveNegotiations() {
        return hasActiveNegotiations;
    }

    public boolean isSessionPreviewRequested() {
        return sessionPreviewRequested;
    }

}
