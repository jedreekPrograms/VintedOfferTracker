package pl.flipbot.playwright.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BotDetailsDto {

    private Long id;

    private String name;

    private String email;

    private String password;

    private BotConfigurationDto configuration;

}