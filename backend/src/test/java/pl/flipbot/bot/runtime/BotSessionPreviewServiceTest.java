package pl.flipbot.bot.runtime;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BotSessionPreviewServiceTest {

    private final BotSessionPreviewService service = new BotSessionPreviewService();

    @Test
    void enablesAndDisablesPreviewPerBot() {
        assertFalse(service.isPreviewRequested(10L));

        service.setPreviewRequested(10L, true);

        assertTrue(service.isPreviewRequested(10L));
        assertFalse(service.isPreviewRequested(11L));

        service.setPreviewRequested(10L, false);

        assertFalse(service.isPreviewRequested(10L));
    }

    @Test
    void removesPreviewRequestsForBotsThatAreNoLongerRunning() {
        service.setPreviewRequested(10L, true);
        service.setPreviewRequested(11L, true);

        service.retainRunningBots(List.of(11L, 12L));

        assertFalse(service.isPreviewRequested(10L));
        assertTrue(service.isPreviewRequested(11L));
    }

    @Test
    void rejectsInvalidBotIds() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.setPreviewRequested(null, true)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> service.setPreviewRequested(0L, true)
        );
    }
}
