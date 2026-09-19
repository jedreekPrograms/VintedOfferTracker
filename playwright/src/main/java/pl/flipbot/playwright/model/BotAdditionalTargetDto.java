package pl.flipbot.playwright.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BotAdditionalTargetDto extends BotConfigurationDto {

    private Long additionalTargetId;

    private Boolean active;
}
