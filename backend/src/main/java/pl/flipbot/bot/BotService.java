package pl.flipbot.bot;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BotService {

    private final BotRepository botRepository;

    public List<Bot> getAllBots() {
        return botRepository.findAll();
    }

    public Bot createBot(Bot bot) {
        return botRepository.save(bot);
    }
}
