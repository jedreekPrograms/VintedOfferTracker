package pl.flipbot.bot.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class BotEditCapabilitiesResponse {

    /** Any active negotiation on the shared Vinted account. */
    private boolean hasActiveNegotiations;

    /**
     * Active negotiation belonging specifically to the original/main product.
     * Additional-product conversations keep the shared account identity locked,
     * but must not freeze unrelated main-product target/strategy fields.
     */
    private boolean hasMainProductActiveNegotiations;

    /**
     * Lowest valid global automatic negotiation cap for the saved main ladder.
     *
     * In adaptive mode the cap cannot be lower than the configured first
     * negotiation step, otherwise a newly-started negotiation could begin
     * above its own global cap. This value is intentionally independent of
     * prices already sent in active negotiations: lowering the cap does not
     * retract historical offers, it only constrains future automatic actions.
     */
    private BigDecimal minimumNegotiationCap;
}
