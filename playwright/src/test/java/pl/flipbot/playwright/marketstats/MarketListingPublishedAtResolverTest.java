package pl.flipbot.playwright.marketstats;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketListingPublishedAtResolverTest {

    private static final LocalDateTime REFERENCE =
            LocalDateTime.of(2026, 9, 9, 16, 30);

    @Test
    void parsesPolishHoursAgo() {
        assertEquals(
                LocalDateTime.of(2026, 9, 9, 1, 30),
                MarketListingPublishedAtResolver
                        .parsePublishedAt("Dodane 15 godzin temu", REFERENCE)
                        .orElseThrow()
        );
    }

    @Test
    void parsesPolishMinutesAgoAcrossWhitespace() {
        assertEquals(
                LocalDateTime.of(2026, 9, 9, 16, 25),
                MarketListingPublishedAtResolver
                        .parsePublishedAt("Dodane\n5 minut temu", REFERENCE)
                        .orElseThrow()
        );
    }

    @Test
    void parsesDaysAgoIntoCurrentWeek() {
        assertEquals(
                LocalDateTime.of(2026, 9, 8, 16, 30),
                MarketListingPublishedAtResolver
                        .parsePublishedAt("Dodane 1 dzień temu", REFERENCE)
                        .orElseThrow()
        );
    }

    @Test
    void parsesExplicitPolishDate() {
        assertEquals(
                LocalDateTime.of(2026, 9, 2, 13, 45),
                MarketListingPublishedAtResolver
                        .parsePublishedAt("Dodane 2 września 2026 o 13:45", REFERENCE)
                        .orElseThrow()
        );
    }

    @Test
    void rejectsPageWithoutPublicationLabel() {
        assertTrue(
                MarketListingPublishedAtResolver
                        .parsePublishedAt("Ostatnie logowanie 15 minut temu", REFERENCE)
                        .isEmpty()
        );
    }
}
