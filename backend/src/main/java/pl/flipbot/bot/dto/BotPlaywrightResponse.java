package pl.flipbot.bot.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BotPlaywrightResponse {

    private Long id;

    private String name;

    private String email;

    private String password;

    private BotConfigurationResponse configuration;

}