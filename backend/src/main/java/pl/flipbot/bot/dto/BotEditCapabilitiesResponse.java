package pl.flipbot.bot.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class BotEditCapabilitiesResponse {

    private boolean hasActiveNegotiations;

    /**
     * When active negotiations exist, the global automatic negotiation cap
     * cannot be lowered below the highest price already sent by the bot.
     */
    private BigDecimal minimumNegotiationCap;
}
