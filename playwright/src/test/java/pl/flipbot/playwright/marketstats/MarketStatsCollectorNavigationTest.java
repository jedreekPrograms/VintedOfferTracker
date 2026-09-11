package pl.flipbot.playwright.marketstats;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitUntilState;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MarketStatsCollectorNavigationTest {

    @Test
    public void catalogPaginationWaitsOnlyForDomContentLoaded() {
        Page.NavigateOptions options =
                MarketStatsCollector.catalogPageNavigateOptions();

        assertEquals(
                WaitUntilState.DOMCONTENTLOADED,
                options.waitUntil
        );
        assertEquals(
                Double.valueOf(30_000),
                options.timeout
        );
    }
}
