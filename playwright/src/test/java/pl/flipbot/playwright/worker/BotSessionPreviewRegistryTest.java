package pl.flipbot.playwright.worker;

import org.junit.Test;
import pl.flipbot.playwright.model.RunningBotDto;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BotSessionPreviewRegistryTest {

    @Test
    public void previewAppliesOnlyToExplicitlyRequestedBot() {
        BotSessionPreviewRegistry registry = new BotSessionPreviewRegistry();

        RunningBotDto previewed = bot(7L, true);
        RunningBotDto normal = bot(8L, false);

        registry.replaceFrom(List.of(previewed, normal));

        assertTrue(registry.isPreviewRequested(7L));
        assertFalse(registry.isPreviewRequested(8L));
        assertFalse(registry.isPreviewRequested(9L));
    }

    @Test
    public void replacingSnapshotRemovesOldPreviewRequest() {
        BotSessionPreviewRegistry registry = new BotSessionPreviewRegistry();

        registry.replaceFrom(List.of(bot(7L, true)));
        assertTrue(registry.isPreviewRequested(7L));

        registry.replaceFrom(List.of(bot(7L, false)));
        assertFalse(registry.isPreviewRequested(7L));
    }

    private RunningBotDto bot(Long id, boolean previewRequested) {
        RunningBotDto bot = new RunningBotDto();
        bot.setId(id);
        bot.setSessionPreviewRequested(previewRequested);
        return bot;
    }
}
