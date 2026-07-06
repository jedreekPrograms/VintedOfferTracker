package pl.flipbot.bot.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BotResponse {

    private Long id;

    private String name;

    private String email;

    private String status;
}
