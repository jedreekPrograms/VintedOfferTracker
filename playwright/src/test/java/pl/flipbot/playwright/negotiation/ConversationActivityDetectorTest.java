package pl.flipbot.playwright.negotiation;

import org.junit.Test;

import java.time.LocalDateTime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ConversationActivityDetectorTest {

    private final ConversationActivityDetector detector =
            new ConversationActivityDetector(null);

    @Test
    public void parsesSingleDigitDayFromCurrentVintedFormat() {
        assertEquals(
                LocalDateTime.of(2026, 9, 1, 12, 27, 39),
                detector.parseVintedTimestamp("1.09.2026, 12:27:39")
        );
    }

    @Test
    public void parsesZeroPaddedDayAndMonth() {
        assertEquals(
                LocalDateTime.of(2026, 9, 1, 12, 27, 39),
                detector.parseVintedTimestamp("01.09.2026, 12:27:39")
        );
    }

    @Test
    public void parsesSingleDigitDayAndMonth() {
        assertEquals(
                LocalDateTime.of(2026, 9, 1, 8, 5, 19),
                detector.parseVintedTimestamp("1.9.2026, 08:05:19")
        );
    }

    @Test
    public void invalidTimestampFailsClosedToNull() {
        assertNull(
                detector.parseVintedTimestamp("dzisiaj rano")
        );
    }
}
