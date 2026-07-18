package pl.flipbot.playwright.negotiation;

import com.microsoft.playwright.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.model.BotDetailsDto;

@Slf4j
@RequiredArgsConstructor
public class NegotiationExecutor {

    private final Page page;
    private final BotDetailsDto bot;

    public void processNegotiations() {

        log.info(
                "Processing negotiations for bot {}",
                bot.getId()
        );
    }
}
