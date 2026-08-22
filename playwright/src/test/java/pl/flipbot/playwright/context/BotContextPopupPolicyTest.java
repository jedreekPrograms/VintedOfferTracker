package pl.flipbot.playwright.context;

import org.junit.Test;
import pl.flipbot.playwright.model.BotDetailsDto;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BotContextPopupPolicyTest {

    @Test
    public void preemptivePopupScriptBlocksWindowOpenAndBlankTargets() {
        String script = BotContext.preemptivePopupSuppressionScript();

        assertTrue(script.contains("window.open = () => null"));
        assertTrue(script.contains("_blank"));
        assertTrue(script.contains("document.addEventListener(\"click\""));
        assertTrue(script.contains("document.addEventListener(\"submit\""));
    }

    @Test
    public void anonymousMarketObserverNeverRestoresStoredAccountSession() {
        BotDetailsDto observer = new BotDetailsDto();
        observer.setId(5L);
        observer.setName("Anonymous Market Observer");
        observer.setEmail(null);
        observer.setPassword(null);

        assertFalse(BotContext.shouldRestoreStoredSession(observer));
    }

    @Test
    public void normalBotStillRestoresItsStoredSession() {
        BotDetailsDto bot = new BotDetailsDto();
        bot.setId(3L);
        bot.setName("Galaxy S25");
        bot.setEmail("bot@example.com");
        bot.setPassword("secret");

        assertTrue(BotContext.shouldRestoreStoredSession(bot));
    }
}
