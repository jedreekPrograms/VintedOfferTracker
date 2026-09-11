package pl.flipbot.playwright.marketstats;

import org.junit.Test;

import java.time.LocalDateTime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class VintedPublishedAtParserTest {

    private static final LocalDateTime OBSERVED_AT =
            LocalDateTime.of(2026, 9, 11, 19, 40);

    @Test
    public void parsesPolishMinuteAge() {
        assertEquals(
                LocalDateTime.of(2026, 9, 11, 19, 28),
                VintedPublishedAtParser.parse(
                        "REL|12 min",
                        OBSERVED_AT
                ).orElseThrow()
        );
    }

    @Test
    public void parsesPolishHourAge() {
        assertEquals(
                LocalDateTime.of(2026, 9, 11, 11, 40),
                VintedPublishedAtParser.parse(
                        "REL|8 godz.",
                        OBSERVED_AT
                ).orElseThrow()
        );
    }

    @Test
    public void parsesPolishDayAge() {
        assertEquals(
                LocalDateTime.of(2026, 9, 5, 19, 40),
                VintedPublishedAtParser.parse(
                        "REL|6 dni",
                        OBSERVED_AT
                ).orElseThrow()
        );
    }

    @Test
    public void convertsUtcTimestampToWarsawTime() {
        assertEquals(
                LocalDateTime.of(2026, 9, 11, 12, 0),
                VintedPublishedAtParser.parse(
                        "ISO|2026-09-11T10:00:00Z",
                        OBSERVED_AT
                ).orElseThrow()
        );
    }

    @Test
    public void rejectsUnrelatedSellerActivityText() {
        assertTrue(
                VintedPublishedAtParser.parse(
                        "REL|Ostatnie logowanie: 20 minut temu",
                        OBSERVED_AT
                ).isEmpty()
        );
    }
}
