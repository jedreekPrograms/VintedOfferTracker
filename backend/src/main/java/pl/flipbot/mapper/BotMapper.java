package pl.flipbot.mapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.flipbot.bot.Bot;
import pl.flipbot.bot.dto.BotResponse;

@Component
@RequiredArgsConstructor
public class BotMapper {

    private final BotConfigurationMapper botConfigurationMapper;

    public BotResponse map(Bot bot) {

        return BotResponse.builder()
                .id(bot.getId())
                .name(bot.getName())
                .email(bot.getEmail())
                .status(bot.getStatus().name())
                .configuration(
                        botConfigurationMapper.map(bot.getConfiguration())
                )
                .build();
    }
}
