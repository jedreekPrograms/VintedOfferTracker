package pl.flipbot.bot.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class BotPlaywrightResponse {

    private Long id;

    private String name;

    private String email;

    private String password;

    private BotConfigurationResponse configuration;

    /*
     * Includes active and inactive additional products. Inactive targets are
     * not scanned anymore, but must remain available so conversations that
     * started from them keep their original negotiation strategy.
     */
    private List<BotAdditionalTargetResponse> additionalTargets;

}
