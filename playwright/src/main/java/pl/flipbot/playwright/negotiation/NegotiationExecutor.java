package pl.flipbot.playwright.negotiation;

import com.microsoft.playwright.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.model.BotDetailsDto;

@RequiredArgsConstructor
public class NegotiationExecutor {

    private final BotContext context;

    public void processNegotiations() {

        Page page = context.getPage();

        // ...

    }

}
