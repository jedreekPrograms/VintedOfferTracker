package pl.flipbot.playwright.filters;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.filters.category.CategoryNavigator;
import pl.flipbot.playwright.model.BotConfigurationDto;
import pl.flipbot.playwright.model.BotDetailsDto;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class FilterService {

    private static final String VINTED_MODEL =
            "VINTED_MODEL";

    private static final String SEARCH_QUERY =
            "SEARCH_QUERY";


    private static final String VINTED_SEARCH_INPUT_SELECTOR =
            "form[action='/catalog'] input[name='search_text']";


    private static final double URL_PERSIST_TIMEOUT_MS =
            5_000;


    private final Page page;

    private final FilterActions actions;

    private final CategoryNavigator
            categoryNavigator;


    public FilterService(
            BotContext context
    ) {

        this.page =
                context.getPage();

        this.actions =
                new FilterActions(
                        page
                );

        this.categoryNavigator =
                new CategoryNavigator(
                        actions
                );
    }


    public void applyFilters(
            BotDetailsDto bot
    ) {

        if (bot == null) {

            throw new IllegalArgumentException(
                    "Bot cannot be null"
            );
        }


        BotConfigurationDto configuration =
                bot.getConfiguration();


        if (configuration == null) {

            throw new IllegalStateException(
                    "Bot configuration is missing"
            );
        }


        String targetMode =
                resolveTargetMode(
                        configuration
                );


        log.info(
                "[FILTER CONFIG] Bot {} configuration received by "
                        + "Playwright. CategoryPath={}, brand='{}', "
                        + "targetMode={}, model='{}', searchQuery='{}', "
                        + "minPrice={}, maxPrice={}.",
                bot.getId(),
                configuration.getCategoryPath(),
                configuration.getBrand(),
                targetMode,
                configuration.getModel(),
                configuration.getSearchQuery(),
                configuration.getMinPrice(),
                configuration.getMaxPrice()
        );


        /*
         * SEARCH_QUERY na początku, bo Enter przeładowuje /catalog.
         * Po nim ustawiamy normalnie kategorię i markę.
         */
        if (
                SEARCH_QUERY.equals(
                        targetMode
                )
        ) {

            if (!hasSearchQuery(bot)) {

                throw new IllegalStateException(
                        "SEARCH_QUERY target mode requires a non-blank searchQuery"
                );
            }


            applySearchQuery(
                    bot
            );
        }


        if (hasCategory(bot)) {

            log.info(
                    "[FILTER] Applying category: {}",
                    configuration
                            .getCategoryPath()
            );


            applyCategory(
                    bot
            );


            requireUrlParameter(
                    "catalog[]",
                    "Category",
                    URL_PERSIST_TIMEOUT_MS
            );
        }


        if (hasBrand(bot)) {

            log.info(
                    "[FILTER] Applying brand: '{}'",
                    configuration
                            .getBrand()
            );


            applyBrand(
                    bot
            );


            requireUrlParameter(
                    "brand_ids[]",
                    "Brand",
                    URL_PERSIST_TIMEOUT_MS
            );


            log.info(
                    "[FILTER] Brand filter persisted: '{}'. Current URL: {}",
                    configuration
                            .getBrand(),
                    page.url()
            );

        } else {

            log.warn(
                    "[FILTER] Bot {} has no configured brand.",
                    bot.getId()
            );
        }


        if (
                VINTED_MODEL.equals(
                        targetMode
                )
        ) {

            if (!hasModel(bot)) {

                throw new IllegalStateException(
                        "VINTED_MODEL target mode requires a non-blank model"
                );
            }


            log.info(
                    "[FILTER] Applying model: '{}'",
                    configuration
                            .getModel()
            );


            applyModel(
                    bot
            );


            requireUrlParameter(
                    "brand_collection_ids[]",
                    "Model",
                    URL_PERSIST_TIMEOUT_MS
            );


            log.info(
                    "[FILTER] Model filter persisted: '{}'. Current URL: {}",
                    configuration
                            .getModel(),
                    page.url()
            );
        }


        if (hasPrice(bot)) {

            log.info(
                    "[FILTER] Applying price range. Min={}, max={}.",
                    configuration
                            .getMinPrice(),
                    configuration
                            .getMaxPrice()
            );


            applyPrice(
                    bot
            );
        }


        log.info(
                "[FILTER] Applying sort: newest first."
        );


        applySortBy();


        verifyFinalFilters(
                bot,
                targetMode
        );


        log.info(
                "[FILTER] Finished applying filters for bot {}. "
                        + "Final catalog URL: {}",
                bot.getId(),
                page.url()
        );
    }


    private void applyCategory(
            BotDetailsDto bot
    ) {

        categoryNavigator.select(
                bot.getConfiguration()
                        .getCategoryPath()
        );
    }


    private void applyBrand(
            BotDetailsDto bot
    ) {

        actions.openFilter(
                FilterSelectors.BRAND_FILTER
        );


        actions.waitForOption(
                bot.getConfiguration()
                        .getBrand()
        );


        actions.selectOption(
                bot.getConfiguration()
                        .getBrand()
        );


        actions.clickSelector(
                FilterSelectors.FILTER_SELECTION
        );
    }


    private void applyModel(
            BotDetailsDto bot
    ) {

        String model =
                bot.getConfiguration()
                        .getModel();


        actions.openFilter(
                FilterSelectors.MODEL_FILTER
        );


        actions.fillInputBySelector(
                FilterSelectors.MODEL_SEARCH_INPUT,
                model
        );


        log.info(
                "[FILTER] Searching Vinted model option for '{}'.",
                model
        );


        actions.clickModel(
                model
        );


        log.info(
                "[FILTER] Vinted model option clicked for '{}'.",
                model
        );


        actions.clickConfirmButton();
    }


    private void applySearchQuery(
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

        Locator visibleHeaderInputs =
                page.locator(
                        "header "
                                + VINTED_SEARCH_INPUT_SELECTOR
                                + ":visible"
                );


        int visibleHeaderCount =
                visibleHeaderInputs.count();


        if (visibleHeaderCount > 0) {

            log.info(
                    "[FILTER SEARCH] Found {} visible search input(s) "
                            + "inside <header>. Using the first one.",
                    visibleHeaderCount
            );


            return visibleHeaderInputs.first();
        }


        Locator visibleSearchInputs =
                page.locator(
                        VINTED_SEARCH_INPUT_SELECTOR
                                + ":visible"
                );


        int visibleSearchInputCount =
                visibleSearchInputs.count();


        log.info(
                "[FILTER SEARCH] No visible header search input found. "
                        + "Visible /catalog search inputs: {}.",
                visibleSearchInputCount
        );


        if (visibleSearchInputCount == 0) {

            int allSearchInputCount =
                    page.locator(
                                    VINTED_SEARCH_INPUT_SELECTOR
                            )
                            .count();


            throw new IllegalStateException(
                    "Vinted search input is not visible. "
                            + "Matching /catalog search inputs in DOM: "
                            + allSearchInputCount
            );
        }


        return visibleSearchInputs.first();
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
                    getUrlParameter(
                            "search_text"
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


    private void applySortBy() {

        try {

            actions.openFilter(
                    FilterSelectors.SORT_BY
            );


            actions.clickSelector(
                    FilterSelectors.SORT_BY_NEWEST
            );


            if (
                    waitForUrlParameterValue(
                            "order",
                            "newest_first",
                            4_000
                    )
            ) {

                log.info(
                        "[FILTER SORT] newest_first persisted through UI. "
                                + "Current URL: {}",
                        page.url()
                );


                return;
            }

        } catch (RuntimeException exception) {

            if (page.isClosed()) {

                throw exception;
            }


            log.warn(
                    "[FILTER SORT] Could not apply newest_first through Vinted UI. "
                            + "Falling back to the catalog URL. reason={}",
                    exception.getMessage()
            );
        }


        String currentUrl =
                page.url();


        String correctedUrl =
                withOrReplacedUrlParameter(
                        currentUrl,
                        "order",
                        "newest_first"
                );


        log.warn(
                "[FILTER SORT] Applying newest_first URL fallback: {} -> {}",
                currentUrl,
                correctedUrl
        );


        page.navigate(
                correctedUrl
        );


        actions.waitForTimeout(
                1_000
        );


        if (
                !"newest_first".equals(
                        getUrlParameter(
                                "order"
                        )
                )
        ) {

            throw new IllegalStateException(
                    "Could not apply newest_first sorting. URL: "
                            + page.url()
            );
        }
    }


    private void applyPrice(
            BotDetailsDto bot
    ) {

        BotConfigurationDto configuration =
                bot.getConfiguration();


        try {
            actions.openFilter(
                    FilterSelectors.PRICE_FILTER
            );


            Locator inputToCommit =
                    null;


            if (
                    configuration.getMinPrice()
                            != null
            ) {

                actions.fillInputBySelector(
                        FilterSelectors.MIN_PRICE,
                        configuration.getMinPrice()
                );


                inputToCommit =
                        page.locator(
                                FilterSelectors.MIN_PRICE
                        );
            }


            if (
                    configuration.getMaxPrice()
                            != null
            ) {

                actions.fillInputBySelector(
                        FilterSelectors.MAX_PRICE,
                        configuration.getMaxPrice()
                );


                inputToCommit =
                        page.locator(
                                FilterSelectors.MAX_PRICE
                        );
            }


            if (inputToCommit != null) {

                inputToCommit.press(
                        "Enter"
                );


                actions.waitForTimeout(
                        1_000
                );
            }


            actions.clickOutsideSafely();


            actions.waitForTimeout(
                    1_000
            );

        } catch (RuntimeException exception) {
            if (page.isClosed()) {
                throw exception;
            }

            log.warn(
                    "[FILTER PRICE] Could not fully apply the configured price range through Vinted UI. "
                            + "Falling back to exact catalog URL parameters. reason={}",
                    exception.getMessage()
            );
        }


        String currentUrl = page.url();
        String correctedUrl = currentUrl;


        if (configuration.getMinPrice() != null
                && !urlPriceMatches(
                "price_from",
                configuration.getMinPrice()
        )) {
            correctedUrl = withOrReplacedUrlParameter(
                    correctedUrl,
                    "price_from",
                    configuration.getMinPrice().toPlainString()
            );
        }


        if (configuration.getMaxPrice() != null
                && !urlPriceMatches(
                "price_to",
                configuration.getMaxPrice()
        )) {
            correctedUrl = withOrReplacedUrlParameter(
                    correctedUrl,
                    "price_to",
                    configuration.getMaxPrice().toPlainString()
            );
        }


        if (!correctedUrl.equals(currentUrl)) {
            log.warn(
                    "[FILTER PRICE] Applying exact price URL fallback: {} -> {}",
                    currentUrl,
                    correctedUrl
            );

            page.navigate(correctedUrl);
            actions.waitForTimeout(1_000);
        }


        requireUrlPrice(
                "price_from",
                configuration.getMinPrice(),
                "minPrice"
        );
        requireUrlPrice(
                "price_to",
                configuration.getMaxPrice(),
                "maxPrice"
        );


        log.info(
                "[FILTER PRICE] Price filter completed. Current URL: {}",
                page.url()
        );
    }


    private void verifyFinalFilters(
            BotDetailsDto bot,
            String targetMode
    ) {

        BotConfigurationDto configuration =
                bot.getConfiguration();


        if (
                SEARCH_QUERY.equals(
                        targetMode
                )
        ) {

            String expectedSearchQuery =
                    normalizeSearchQuery(
                            configuration.getSearchQuery()
                    );


            String actualSearchQuery =
                    getUrlParameter(
                            "search_text"
                    );


            if (
                    !expectedSearchQuery.equals(
                            actualSearchQuery
                    )
            ) {

                throw new IllegalStateException(
                        "Final Vinted catalog URL lost SEARCH_QUERY. "
                                + "Expected search_text='"
                                + expectedSearchQuery
                                + "', actual='"
                                + actualSearchQuery
                                + "'. URL: "
                                + page.url()
                );
            }
        }


        if (
                hasCategory(bot)
                        && !hasUrlParameter(
                        "catalog[]"
                )
        ) {

            throw new IllegalStateException(
                    "Final Vinted catalog URL does not contain a category filter. "
                            + "URL: "
                            + page.url()
            );
        }


        if (
                hasBrand(bot)
                        && !hasUrlParameter(
                        "brand_ids[]"
                )
        ) {

            throw new IllegalStateException(
                    "Final Vinted catalog URL does not contain a brand filter. "
                            + "Configured brand: '"
                            + configuration.getBrand()
                            + "'. URL: "
                            + page.url()
            );
        }


        if (
                VINTED_MODEL.equals(
                        targetMode
                )
                        && !hasUrlParameter(
                        "brand_collection_ids[]"
                )
        ) {

            throw new IllegalStateException(
                    "Final Vinted catalog URL does not contain a model filter. "
                            + "Configured model: '"
                            + configuration.getModel()
                            + "'. URL: "
                            + page.url()
            );
        }


        requireUrlPrice(
                "price_from",
                configuration.getMinPrice(),
                "minPrice"
        );
        requireUrlPrice(
                "price_to",
                configuration.getMaxPrice(),
                "maxPrice"
        );


        String order =
                getUrlParameter(
                        "order"
                );


        if (
                !"newest_first".equals(
                        order
                )
        ) {

            throw new IllegalStateException(
                    "Final Vinted catalog URL does not use newest_first sorting. "
                            + "Actual order='"
                            + order
                            + "'. URL: "
                            + page.url()
            );
        }


        log.info(
                "[FILTER VERIFY] Final filters verified. "
                        + "targetMode={}, searchQuery='{}', category={}, "
                        + "brand='{}', model='{}', minPrice={}, maxPrice={}, "
                        + "order={}.",
                targetMode,
                configuration.getSearchQuery(),
                configuration.getCategoryPath(),
                configuration.getBrand(),
                configuration.getModel(),
                configuration.getMinPrice(),
                configuration.getMaxPrice(),
                order
        );
    }


    private void requireUrlPrice(
            String parameterName,
            BigDecimal expectedPrice,
            String label
    ) {
        if (expectedPrice == null) {
            return;
        }

        String actual = getUrlParameter(parameterName);

        if (actual == null || actual.isBlank()) {
            throw new IllegalStateException(
                    "Final Vinted catalog URL does not contain "
                            + parameterName
                            + ". Configured "
                            + label
                            + "="
                            + expectedPrice
                            + ". URL: "
                            + page.url()
            );
        }

        try {
            BigDecimal actualPrice = new BigDecimal(actual);

            if (actualPrice.compareTo(expectedPrice) != 0) {
                throw new IllegalStateException(
                        "Final Vinted catalog URL contains unexpected "
                                + parameterName
                                + ". Expected "
                                + expectedPrice
                                + ", actual "
                                + actual
                                + ". URL: "
                                + page.url()
                );
            }
        } catch (NumberFormatException exception) {
            throw new IllegalStateException(
                    "Final Vinted catalog URL contains invalid "
                            + parameterName
                            + "='"
                            + actual
                            + "'. URL: "
                            + page.url(),
                    exception
            );
        }
    }


    private boolean urlPriceMatches(
            String parameterName,
            BigDecimal expectedPrice
    ) {
        if (expectedPrice == null) {
            return true;
        }

        String actual = getUrlParameter(parameterName);

        if (actual == null || actual.isBlank()) {
            return false;
        }

        try {
            return new BigDecimal(actual).compareTo(expectedPrice) == 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }


    private void requireUrlParameter(
            String parameterName,
            String label,
            double timeoutMilliseconds
    ) {

        if (
                waitForUrlParameterPresent(
                        parameterName,
                        timeoutMilliseconds
                )
        ) {

            log.info(
                    "[FILTER VERIFY] {} persisted in URL. {}={}",
                    label,
                    parameterName,
                    getUrlParameter(
                            parameterName
                    )
            );


            return;
        }


        throw new IllegalStateException(
                label
                        + " filter action finished, but URL parameter '"
                        + parameterName
                        + "' is missing. URL: "
                        + page.url()
        );
    }


    private boolean waitForUrlParameterPresent(
            String parameterName,
            double timeoutMilliseconds
    ) {

        long deadline =
                System.currentTimeMillis()
                        + (long) timeoutMilliseconds;


        while (
                System.currentTimeMillis()
                        <= deadline
        ) {

            if (
                    hasUrlParameter(
                            parameterName
                    )
            ) {

                return true;
            }


            actions.waitForTimeout(
                    200
            );
        }


        return false;
    }


    private boolean waitForUrlParameterValue(
            String parameterName,
            String expectedValue,
            double timeoutMilliseconds
    ) {

        long deadline =
                System.currentTimeMillis()
                        + (long) timeoutMilliseconds;


        while (
                System.currentTimeMillis()
                        <= deadline
        ) {

            if (
                    expectedValue.equals(
                            getUrlParameter(
                                    parameterName
                            )
                    )
            ) {

                return true;
            }


            actions.waitForTimeout(
                    200
            );
        }


        return false;
    }


    private boolean hasUrlParameter(
            String parameterName
    ) {

        return getUrlParameter(
                parameterName
        )
                != null;
    }


    private String getUrlParameter(
            String parameterName
    ) {

        String currentUrl =
                page.url();


        int questionMarkIndex =
                currentUrl.indexOf(
                        '?'
                );


        if (
                questionMarkIndex < 0
                        || questionMarkIndex
                        == currentUrl.length() - 1
        ) {

            return null;
        }


        String query =
                currentUrl.substring(
                        questionMarkIndex + 1
                );


        int fragmentIndex =
                query.indexOf(
                        '#'
                );


        if (
                fragmentIndex >= 0
        ) {

            query =
                    query.substring(
                            0,
                            fragmentIndex
                    );
        }


        for (
                String parameter
                : query.split(
                "&"
        )
        ) {

            int equalsIndex =
                    parameter.indexOf(
                            '='
                    );


            String rawName =
                    equalsIndex >= 0
                            ? parameter.substring(
                            0,
                            equalsIndex
                    )
                            : parameter;


            String rawValue =
                    equalsIndex >= 0
                            ? parameter.substring(
                            equalsIndex + 1
                    )
                            : "";


            String decodedName =
                    URLDecoder.decode(
                            rawName,
                            StandardCharsets.UTF_8
                    );


            if (
                    !parameterName.equals(
                            decodedName
                    )
            ) {

                continue;
            }


            return URLDecoder.decode(
                    rawValue,
                    StandardCharsets.UTF_8
            );
        }


        return null;
    }


    private String withOrReplacedUrlParameter(
            String url,
            String parameterName,
            String parameterValue
    ) {

        String fragment =
                "";


        String withoutFragment =
                url;


        int fragmentIndex =
                url.indexOf(
                        '#'
                );


        if (
                fragmentIndex >= 0
        ) {

            fragment =
                    url.substring(
                            fragmentIndex
                    );


            withoutFragment =
                    url.substring(
                            0,
                            fragmentIndex
                    );
        }


        String encodedParameterName =
                Pattern.quote(
                        parameterName
                );


        Pattern pattern =
                Pattern.compile(
                        "([?&])"
                                + encodedParameterName
                                + "=[^&#]*"
                );


        Matcher matcher =
                pattern.matcher(
                        withoutFragment
                );


        if (matcher.find()) {

            return matcher.replaceFirst(
                    "$1"
                            + parameterName
                            + "="
                            + parameterValue
            )
                    + fragment;
        }


        String separator =
                withoutFragment.contains(
                        "?"
                )
                        ? "&"
                        : "?";


        return withoutFragment
                + separator
                + parameterName
                + "="
                + parameterValue
                + fragment;
    }


    private String normalizeSearchQuery(
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


    private String resolveTargetMode(
            BotConfigurationDto configuration
    ) {

        String targetMode =
                configuration.getTargetMode();


        if (
                targetMode == null
                        || targetMode.isBlank()
        ) {

            return VINTED_MODEL;
        }


        String normalizedMode =
                targetMode
                        .trim()
                        .toUpperCase(
                                Locale.ROOT
                        );


        if (
                VINTED_MODEL.equals(
                        normalizedMode
                )
                        || SEARCH_QUERY.equals(
                        normalizedMode
                )
        ) {

            return normalizedMode;
        }


        throw new IllegalStateException(
                "Unsupported target mode: "
                        + targetMode
        );
    }


    private boolean hasCategory(
            BotDetailsDto bot
    ) {

        return bot.getConfiguration()
                .getCategoryPath()
                != null
                && !bot.getConfiguration()
                .getCategoryPath()
                .isEmpty();
    }


    private boolean hasBrand(
            BotDetailsDto bot
    ) {

        String brand =
                bot.getConfiguration()
                        .getBrand();


        return brand != null
                && !brand.isBlank();
    }


    private boolean hasModel(
            BotDetailsDto bot
    ) {

        String model =
                bot.getConfiguration()
                        .getModel();


        return model != null
                && !model.isBlank();
    }


    private boolean hasSearchQuery(
            BotDetailsDto bot
    ) {

        String searchQuery =
                bot.getConfiguration()
                        .getSearchQuery();


        return searchQuery != null
                && !searchQuery.isBlank();
    }


    private boolean hasPrice(
            BotDetailsDto bot
    ) {

        return bot.getConfiguration()
                .getMinPrice()
                != null
                || bot.getConfiguration()
                .getMaxPrice()
                != null;
    }
}
