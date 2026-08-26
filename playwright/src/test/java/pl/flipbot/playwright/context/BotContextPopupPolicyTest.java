package pl.flipbot.playwright.context;

import org.junit.Test;
import pl.flipbot.playwright.model.BotDetailsDto;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BotContextPopupPolicyTest {

    @Test
    public void anonymousObserverUiScriptAcceptsOneTrustAndNormalizesModelRows() {
        String script = BotContext.anonymousObserverUiStabilityScript();

        assertTrue(script.contains("#onetrust-accept-btn-handler"));
        assertTrue(script.contains("zgoda na wszystkie"));
        assertTrue(script.contains("accept all"));
        assertTrue(script.contains("MutationObserver"));
        assertTrue(script.contains("selectable-item-brand_collection-"));
        assertTrue(script.contains("ids.size === 1 && ids.has(collectionId)"));
        assertTrue(script.contains("candidate.setAttribute(\"title\", associatedText)"));
        assertTrue(script.contains("pokaż wyniki"));
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
