package pl.flipbot.playwright.processing;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CatalogWorkProcessorRotationStateTest {

    private static final long BOT_ID = 501L;
    private static final long OTHER_BOT_ID = 502L;

    @After
    public void tearDown() {
        CatalogWorkProcessor.clearRotationState(BOT_ID);
        CatalogWorkProcessor.clearRotationState(OTHER_BOT_ID);
    }

    @Test
    public void clearingStoppedBotRotationRestartsFromFirstProduct() {
        assertEquals(0, CatalogWorkProcessor.nextProductOffsetForRun(BOT_ID, 5));
        assertEquals(1, CatalogWorkProcessor.nextProductOffsetForRun(BOT_ID, 5));
        assertEquals(2, CatalogWorkProcessor.nextProductOffsetForRun(BOT_ID, 5));

        CatalogWorkProcessor.clearRotationState(BOT_ID);

        assertEquals(0, CatalogWorkProcessor.nextProductOffsetForRun(BOT_ID, 5));
    }

    @Test
    public void clearingOneBotDoesNotResetAnotherBotRotation() {
        assertEquals(0, CatalogWorkProcessor.nextProductOffsetForRun(BOT_ID, 3));
        assertEquals(0, CatalogWorkProcessor.nextProductOffsetForRun(OTHER_BOT_ID, 3));
        assertEquals(1, CatalogWorkProcessor.nextProductOffsetForRun(OTHER_BOT_ID, 3));

        CatalogWorkProcessor.clearRotationState(BOT_ID);

        assertEquals(2, CatalogWorkProcessor.nextProductOffsetForRun(OTHER_BOT_ID, 3));
        assertEquals(0, CatalogWorkProcessor.nextProductOffsetForRun(BOT_ID, 3));
    }
}
