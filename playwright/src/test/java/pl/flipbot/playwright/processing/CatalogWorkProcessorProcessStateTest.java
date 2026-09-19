package pl.flipbot.playwright.processing;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CatalogWorkProcessorProcessStateTest {

    private static final long BOT_ID = 41L;
    private static final long OTHER_BOT_ID = 42L;

    @After
    public void tearDown() {
        CatalogWorkProcessor.clearProcessState(BOT_ID);
        CatalogWorkProcessor.clearProcessState(OTHER_BOT_ID);
    }

    @Test
    public void clearsOnlyRequestedBotRotationState() {
        CatalogWorkProcessor.setRotationStateForTests(BOT_ID, 3);
        CatalogWorkProcessor.setRotationStateForTests(OTHER_BOT_ID, 2);

        CatalogWorkProcessor.clearProcessState(BOT_ID);

        assertFalse(CatalogWorkProcessor.hasRotationStateForTests(BOT_ID));
        assertTrue(CatalogWorkProcessor.hasRotationStateForTests(OTHER_BOT_ID));
    }
}
