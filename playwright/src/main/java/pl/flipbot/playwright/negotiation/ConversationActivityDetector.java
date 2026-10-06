package pl.flipbot.playwright.negotiation;

import lombok.RequiredArgsConstructor;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.privatecore.PrivateVintedCoreBridge;

/**
 * Public shell around the private Vinted activity inspector.
 *
 * The DOM selectors and JavaScript inspection logic deliberately live in the
 * private core repository.
 */
@RequiredArgsConstructor
public class ConversationActivityDetector {

    private final BotContext context;

    public ConversationActivitySnapshot inspect() {
        return PrivateVintedCoreBridge.inspectActivity(
                context.getPage()
        );
    }
}
