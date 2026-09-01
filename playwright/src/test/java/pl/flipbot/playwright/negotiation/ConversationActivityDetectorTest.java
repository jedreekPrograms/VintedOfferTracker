package pl.flipbot.playwright.negotiation;

import org.junit.Test;

import java.time.LocalDateTime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ConversationActivityDetectorTest {

    @Test
    public void parsesSingleDigitDayAndPaddedMonth() {
        assertEquals(
                LocalDateTime.of(2026, 9, 1, 12, 27, 39),
                ConversationActivityDetector.parseVintedTimestamp(
                        "1.09.2026, 12:27:39"
                )
        );
    }

    @Test
    public void parsesPaddedDayAndMonth() {
        assertEquals(
                LocalDateTime.of(2026, 8, 10, 20, 22, 50),
                ConversationActivityDetector.parseVintedTimestamp(
                        "10.08.2026, 20:22:50"
                )
        );
    }

    @Test
    public void parsesSingleDigitDayAndMonth() {
        assertEquals(
                LocalDateTime.of(2026, 9, 1, 8, 5, 19),
                ConversationActivityDetector.parseVintedTimestamp(
                        "1.9.2026, 08:05:19"
                )
        );
    }

    @Test
    public void invalidTimestampFailsClosed() {
        assertNull(
                ConversationActivityDetector.parseVintedTimestamp(
                        "dzisiaj, 12:27"
                )
        );
    }
}
