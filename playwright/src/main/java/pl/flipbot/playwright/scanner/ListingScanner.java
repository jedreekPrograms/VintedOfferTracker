package pl.flipbot.playwright.scanner;

import com.microsoft.playwright.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.model.BotDetailsDto;

@Slf4j
@RequiredArgsConstructor
public class ListingScanner {

    private final Page page;
    private final BotDetailsDto bot;

    public void scan() {

        log.info(
                "Scanning listings for bot {}",
                bot.getId()
        );
    }
}
