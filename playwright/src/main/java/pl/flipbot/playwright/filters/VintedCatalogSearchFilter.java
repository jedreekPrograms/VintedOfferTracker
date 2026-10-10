package pl.flipbot.playwright.filters;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.model.BotDetailsDto;

/**
 * Text-search application only: visible native input, exact typed echo,
 * Enter navigation and search_text persistence evidence.
 *
 * Unlike VINTED_MODEL, SEARCH_QUERY still requires downstream semantic product
 * verification before real offers. This class does not assert product identity.
 */
@Slf4j
final class VintedCatalogSearchFilter {
    private final Page page;
    private final FilterActions actions;

    VintedCatalogSearchFilter(Page page, FilterActions actions) {
        this.page = page;
        this.actions = actions;
    }

    void apply(
            BotDetailsDto bot
    ) {

        String searchQuery =
                normalizeSearchQuery(
                        bot.getConfiguration()
                                .getSearchQuery()
                );


        log.info(
                "[FILTER SEARCH] Applying text search query: '{}'.",
                searchQuery
        );


        Locator searchInput =
                resolveVisibleSearchInput();


        searchInput.waitFor(
                new Locator.WaitForOptions()
                        .setState(
                                WaitForSelectorState.VISIBLE
                        )
                        .setTimeout(
                                5_000
                        )
        );


        log.info(
                "[FILTER SEARCH] Search input found. "
                        + "Placeholder='{}', current value='{}'.",
                searchInput.getAttribute(
                        "placeholder"
                ),
                searchInput.inputValue()
        );


        searchInput.click();


        searchInput.fill(
                searchQuery
        );


        String enteredValue =
                searchInput.inputValue();


        if (
                !searchQuery.equals(
                        enteredValue
                )
        ) {

            throw new IllegalStateException(
                    "Vinted search input contains unexpected value. "
                            + "Expected: '"
                            + searchQuery
                            + "', actual: '"
                            + enteredValue
                            + "'."
            );
        }


        log.info(
                "[FILTER SEARCH] Search query entered successfully. "
                        + "Input value='{}'.",
                enteredValue
        );


        searchInput.press(
                "Enter"
        );


        waitForSearchQueryInUrl(
                searchQuery
        );


        log.info(
                "[FILTER SEARCH] Search query submitted successfully. "
                        + "Current URL: {}",
                page.url()
        );
    }

    private Locator resolveVisibleSearchInput() {
        return VintedCatalogSearchInputResolver.resolveVisible(page);
    }

    private void waitForSearchQueryInUrl(
            String expectedSearchQuery
    ) {

        final int maxAttempts =
                20;

        final double delayMilliseconds =
                250;


        for (
                int attempt = 1;
                attempt <= maxAttempts;
                attempt++
        ) {

            String currentSearchQuery =
                    VintedCatalogUrlParameters.get(
                            page.url(), "search_text"
                    );


            if (
                    expectedSearchQuery.equals(
                            currentSearchQuery
                    )
            ) {

                return;
            }


            actions.waitForTimeout(
                    delayMilliseconds
            );
        }


        throw new IllegalStateException(
                "Vinted did not submit the expected search query. "
                        + "Expected search_text='"
                        + expectedSearchQuery
                        + "', current URL: "
                        + page.url()
        );
    }

    static String normalizeSearchQuery(
            String searchQuery
    ) {

        if (searchQuery == null) {

            return "";
        }


        return searchQuery
                .trim()
                .replaceAll(
                        "\\s+",
                        " "
                );
    }
}
