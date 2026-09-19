package pl.flipbot.bot.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class BotResponse {

    private Long id;

    private String name;

    private String email;

    private String status;

    private BotConfigurationResponse configuration;

    /* Active extras only. Empty for every existing bot until the user opts in. */
    private List<BotAdditionalTargetResponse> additionalTargets;
}
