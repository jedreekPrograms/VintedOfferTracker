package pl.flipbot.playwright.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BotDetailsDto {

    private Long id;

    private String name;

    private String email;

    private String password;

    private BotConfigurationDto configuration;

    private List<BotAdditionalTargetDto> additionalTargets;
}
