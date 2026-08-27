package pl.flipbot.playwright.negotiation;

import org.junit.Test;

import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class RuntimeRegressionTest {

    @Test
    public void soldStatusPatternUsesOnlyPlaywrightSupportedFlags() {
        Pattern pattern = AdaptiveFirstOfferExecutor.soldStatusPattern();

        assertEquals(Pattern.CASE_INSENSITIVE, pattern.flags());
    }

    @Test
    public void unknownConversationSnapshotCanPreserveVisibleRawStatus() {
        NegotiationConversationSnapshot snapshot =
                NegotiationConversationSnapshot.unknown("Anulowane");

        assertEquals(NegotiationConversationResult.UNKNOWN, snapshot.result());
        assertEquals("Anulowane", snapshot.rawStatus());
        assertNull(snapshot.sellerCounterOfferPrice());
    }
}
