package pl.flipbot.bot;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import pl.flipbot.bot.dto.CreateBotRequest;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BotService {

    private final BotRepository botRepository;

    public List<Bot> getAllBots() {
        return botRepository.findAll();
    }

    public Bot createBot(CreateBotRequest request) {
        Bot bot = Bot.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(request.getPassword())
                .status(BotStatus.STOPPED)
                .build();

        return botRepository.save(bot);
    }
}
