package pl.flipbot.playwright.context;

import org.junit.Test;

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
}
