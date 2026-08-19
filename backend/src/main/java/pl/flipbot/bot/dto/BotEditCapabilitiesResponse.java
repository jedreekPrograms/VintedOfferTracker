package pl.flipbot.bot.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class BotEditCapabilitiesResponse {

    private boolean hasActiveNegotiations;

    /**
     * Lowest valid global automatic negotiation cap for the saved ladder.
     *
     * In adaptive mode the cap cannot be lower than the configured first
     * negotiation step, otherwise a newly-started negotiation could begin
     * above its own global cap. This value is intentionally independent of
     * prices already sent in active negotiations: lowering the cap does not
     * retract historical offers, it only constrains future automatic actions.
     */
    private BigDecimal minimumNegotiationCap;
}
