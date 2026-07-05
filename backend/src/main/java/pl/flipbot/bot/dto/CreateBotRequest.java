package pl.flipbot.bot.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateBotRequest {
    private String name;

    private String email;

    private String password;
}
