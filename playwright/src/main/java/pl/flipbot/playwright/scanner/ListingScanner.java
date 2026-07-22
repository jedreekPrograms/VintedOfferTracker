package pl.flipbot.playwright.scanner;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.context.BotContext;

@Slf4j
@RequiredArgsConstructor
public class ListingScanner {

    private final BotContext context;

    public void scan() {

        log.info(
                "Scanning listings for bot {}",
                context.getBot().getId()
        );

        // TODO:
        // 1. Pobierz wszystkie oferty
        // 2. Odfiltruj już odwiedzone
        // 3. Wyślij do NegotiationExecutor

    }

}