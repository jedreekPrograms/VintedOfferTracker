package pl.flipbot.playwright.scanner;

import com.microsoft.playwright.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.model.BotDetailsDto;

@Slf4j
@RequiredArgsConstructor
public class ListingScanner {

    private final BotContext context;

    public void scan() {

        Page page = context.getPage();

        log.info(
                "Scanning listings for bot {}",
                context.getBot().getId()
        );
    }
}
