package pl.flipbot.playwright.filters;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.junit.Test;
import pl.flipbot.playwright.model.BotConfigurationDto;
import pl.flipbot.playwright.model.BotDetailsDto;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class VintedCatalogSearchFilterTest {
    private static final String VISIBLE_HEADER =
            "header [data-testid='search-text--input']:visible";

    @Test
    public void normalizationMatchesLegacySpaceRules() {
        assertEquals("", VintedCatalogSearchFilter.normalizeSearchQuery(null));
        assertEquals("Galaxy S25 FE",
                VintedCatalogSearchFilter.normalizeSearchQuery(" \t Galaxy   S25  FE\n "));
    }

    @Test
    public void successfulSearchUsesVisibleTestIdAndConfirmedUrl() {
        Page page = mock(Page.class);
        FilterActions actions = mock(FilterActions.class);
        Locator matches = mock(Locator.class);
        Locator input = mock(Locator.class);
        when(page.locator(VISIBLE_HEADER)).thenReturn(matches);
        when(matches.count()).thenReturn(1);
        when(matches.first()).thenReturn(input);
        when(input.inputValue()).thenReturn("", "Galaxy S25 FE");
        when(page.url()).thenReturn(
                "https://www.vinted.pl/catalog?search_text=Galaxy+S25+FE");
        BotConfigurationDto config = mock(BotConfigurationDto.class);
        BotDetailsDto bot = mock(BotDetailsDto.class);
        when(bot.getConfiguration()).thenReturn(config);
        when(config.getSearchQuery()).thenReturn(" Galaxy   S25 FE ");

        new VintedCatalogSearchFilter(page, actions).apply(bot);

        verify(input).click();
        verify(input).fill("Galaxy S25 FE");
        verify(input).press("Enter");
        verify(actions, never()).waitForTimeout(250);
    }

    @Test
    public void incorrectInputEchoPreventsEnterSubmission() {
        Page page = mock(Page.class);
        FilterActions actions = mock(FilterActions.class);
        Locator matches = mock(Locator.class);
        Locator input = mock(Locator.class);
        when(page.locator(VISIBLE_HEADER)).thenReturn(matches);
        when(matches.count()).thenReturn(1);
        when(matches.first()).thenReturn(input);
        when(input.inputValue()).thenReturn("", "Wrong");
        BotConfigurationDto config = mock(BotConfigurationDto.class);
        BotDetailsDto bot = mock(BotDetailsDto.class);
        when(bot.getConfiguration()).thenReturn(config);
        when(config.getSearchQuery()).thenReturn("Galaxy S25 FE");

        try {
            new VintedCatalogSearchFilter(page, actions).apply(bot);
            fail("Should reject changed search text");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("unexpected value"));
        }
        verify(input, never()).press("Enter");
    }

    @Test
    public void missingUrlPersistenceDoesNotClaimSearchSuccess() {
        Page page = mock(Page.class);
        FilterActions actions = mock(FilterActions.class);
        Locator matches = mock(Locator.class);
        Locator input = mock(Locator.class);
        when(page.locator(VISIBLE_HEADER)).thenReturn(matches);
        when(matches.count()).thenReturn(1);
        when(matches.first()).thenReturn(input);
        when(input.inputValue()).thenReturn("", "Samsung");
        when(page.url()).thenReturn("https://www.vinted.pl/catalog");
        BotConfigurationDto config = mock(BotConfigurationDto.class);
        BotDetailsDto bot = mock(BotDetailsDto.class);
        when(bot.getConfiguration()).thenReturn(config);
        when(config.getSearchQuery()).thenReturn("Samsung");

        try {
            new VintedCatalogSearchFilter(page, actions).apply(bot);
            fail("Must reject URL without expected search_text");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("did not submit"));
        }
        verify(input).press("Enter");
        verify(actions, times(20)).waitForTimeout(250);
    }
}
