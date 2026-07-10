package pl.flipbot.bot.dto;

import lombok.Builder;
import lombok.Getter;
import pl.flipbot.bot.configuration.BotConfiguration;

@Getter
@Builder
public class BotResponse {

    private Long id;

    private String name;

    private String email;

    private String status;

    private BotConfigurationResponse configuration;
}
