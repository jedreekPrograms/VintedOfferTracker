package pl.flipbot.playwright.filters;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.filters.category.CategoryNavigator;
import pl.flipbot.playwright.model.BotConfigurationDto;
import pl.flipbot.playwright.model.BotDetailsDto;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Slf4j
public class FilterService {

    private static final String VINTED_MODEL =
            "VINTED_MODEL";

    private static final String SEARCH_QUERY =
            "SEARCH_QUERY";

    private static final int FILTER_APPLY_MAX_ATTEMPTS =
            3;

    private static final double FILTER_APPLY_RETRY_DELAY_MS =
            750;

    private static final double URL_PARAMETER_WAIT_MS =
            4_000;

    private static final String VINTED_SEARCH_INPUT_SELECTOR =
            "form[action='/catalog'] input[name='search_text']";


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
         * SEARCH_QUERY wykonujemy na początku.
         *
         * Wyszukiwarka Vinted może przeładować katalog.
         * Dopiero po niej nakładamy kategorię i markę,
         * żeby finalnie zawsze były obecne.
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

            if (hasModel(bot)) {

                log.info(
                        "[FILTER] Applying model: '{}'",
                        configuration
                                .getModel()
                );


                applyModel(
                        bot
                );

            } else {

                throw new IllegalStateException(
                        "VINTED_MODEL target mode requires a non-blank model"
                );
            }
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


        /*
         * Sortowanie nakładamy NA KOŃCU.
         *
         * W realnym logu Vinted potrafiło zgubić order=newest_first
         * po późniejszej zmianie ceny. Dzięki tej kolejności nic już
         * po sortowaniu nie zmienia katalogowych filtrów.
         */
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


        if (
                !waitForUrlParameterPresent(
                        "catalog[]",
                        URL_PARAMETER_WAIT_MS
                )
        ) {

            throw new IllegalStateException(
                    "Vinted category UI completed, but catalog[] "
                            + "was not persisted in the URL. URL: "
                            + page.url()
            );
        }


        log.info(
                "[FILTER] Category filter persisted in URL. Current URL: {}",
                page.url()
        );
    }


    private void applyBrand(
            BotDetailsDto bot
    ) {

        String brand =
                bot.getConfiguration()
                        .getBrand();


        RuntimeException lastException =
                null;


        for (
                int attempt = 1;
                attempt <= FILTER_APPLY_MAX_ATTEMPTS;
                attempt++
        ) {

            actions.dismissOpenOverlaySafely();


            try {

                log.info(
                        "[FILTER BRAND] Applying brand '{}'. Attempt {}/{}.",
                        brand,
                        attempt,
                        FILTER_APPLY_MAX_ATTEMPTS
                );


                actions.openFilter(
                        FilterSelectors.BRAND_FILTER
                );


                actions.waitForOption(
                        brand
                );


                actions.selectOption(
                        brand
                );


                actions.clickConfirmButton();


                if (
                        waitForUrlParameterPresent(
                                "brand_ids[]",
                                URL_PARAMETER_WAIT_MS
                        )
                ) {

                    log.info(
                            "[FILTER BRAND] Brand '{}' persisted in URL. "
                                    + "Current URL: {}",
                            brand,
                            page.url()
                    );


                    return;
                }


                throw new IllegalStateException(
                        "Brand selection did not persist brand_ids[] in URL"
                );

            } catch (RuntimeException exception) {

                lastException =
                        exception;


                log.warn(
                        "[FILTER BRAND] Attempt {}/{} failed for '{}': {}",
                        attempt,
                        FILTER_APPLY_MAX_ATTEMPTS,
                        brand,
                        firstLineMessage(
                                exception
                        )
                );


                actions.dismissOpenOverlaySafely();


                if (
                        attempt < FILTER_APPLY_MAX_ATTEMPTS
                ) {

                    actions.waitForTimeout(
                            FILTER_APPLY_RETRY_DELAY_MS
                    );
                }
            }
        }


        throw new IllegalStateException(
                "Could not apply brand filter after "
                        + FILTER_APPLY_MAX_ATTEMPTS
                        + " attempts. Brand='"
                        + brand
                        + "'. URL: "
                        + page.url(),
                lastException
        );
    }


    private void applyModel(
            BotDetailsDto bot
    ) {

        String model =
                bot.getConfiguration()
                        .getModel();


        RuntimeException lastException =
                null;


        for (
                int attempt = 1;
                attempt <= FILTER_APPLY_MAX_ATTEMPTS;
                attempt++
        ) {

            actions.dismissOpenOverlaySafely();


            try {

                log.info(
                        "[FILTER MODEL] Applying model '{}'. Attempt {}/{}.",
                        model,
                        attempt,
                        FILTER_APPLY_MAX_ATTEMPTS
                );


                actions.openFilter(
                        FilterSelectors.MODEL_FILTER
                );


                actions.fillInputBySelector(
                        FilterSelectors.MODEL_SEARCH_INPUT,
                        model
                );


                log.info(
                        "[FILTER MODEL] Searching Vinted model option for '{}'.",
                        model
                );


                actions.clickModel(
                        model
                );


                actions.clickConfirmButton();


                if (
                        waitForUrlParameterPresent(
                                "brand_collection_ids[]",
                                URL_PARAMETER_WAIT_MS
                        )
                ) {

                    log.info(
                            "[FILTER MODEL] Model '{}' persisted in URL. "
                                    + "Current URL: {}",
                            model,
                            page.url()
                    );


                    return;
                }


                throw new IllegalStateException(
                        "Model selection did not persist "
                                + "brand_collection_ids[] in URL"
                );

            } catch (RuntimeException exception) {

                lastException =
                        exception;


                log.warn(
                        "[FILTER MODEL] Attempt {}/{} failed for '{}': {}",
                        attempt,
                        FILTER_APPLY_MAX_ATTEMPTS,
                        model,
                        firstLineMessage(
                                exception
                        )
                );


                actions.dismissOpenOverlaySafely();


                if (
                        attempt < FILTER_APPLY_MAX_ATTEMPTS
                ) {

                    actions.waitForTimeout(
                            FILTER_APPLY_RETRY_DELAY_MS
                    );
                }
            }
        }


        throw new IllegalStateException(
                "Could not apply model filter after "
                        + FILTER_APPLY_MAX_ATTEMPTS
                        + " attempts. Model='"
                        + model
                        + "'. URL: "
                        + page.url(),
                lastException
        );
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


        /*
         * Vinted renderuje DWA inputy z tym samym:
         *
         * id="search_text"
         * name="search_text"
         * data-testid="search-text--input"
         *
         * Jeden znajduje się w desktopowym <header>,
         * a drugi w dodatkowym pasku wyszukiwania strony.
         *
         * Dlatego NIE wolno używać po prostu:
         *
         * page.locator("#search_text")
         *
         * bo Playwright strict mode widzi dwa elementy.
         *
         * Najpierw preferujemy widoczny input w <header>.
         * Jeśli layout Vinted się zmieni, korzystamy z pierwszego
         * widocznego inputa formularza /catalog.
         */
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


        /*
         * Input znajduje się w formularzu:
         *
         * action="/catalog"
         * method="get"
         * name="search_text"
         *
         * Enter powinien więc przejść do:
         * /catalog?search_text=...
         */
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

        actions.dismissOpenOverlaySafely();


        try {

            actions.openFilter(
                    FilterSelectors.SORT_BY
            );


            actions.clickSelector(
                    FilterSelectors.SORT_BY_NEWEST
            );


            if (
                    waitForUrlParameterEquals(
                            "order",
                            "newest_first",
                            URL_PARAMETER_WAIT_MS
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

            log.warn(
                    "[FILTER SORT] UI sort action failed or did not persist: {}",
                    firstLineMessage(
                            exception
                    )
            );
        }


        /*
         * Bezpieczny fallback.
         *
         * order=newest_first nie wymaga żadnego ukrytego ID,
         * więc możemy zachować wszystkie istniejące parametry
         * (search_text, catalog[], brand_ids[], model, ceny)
         * i tylko dopisać / podmienić order.
         */
        actions.dismissOpenOverlaySafely();


        String currentUrl =
                page.url();


        String correctedUrl =
                withOrReplacedUrlParameter(
                        currentUrl,
                        "order",
                        "newest_first"
                );


        log.warn(
                "[FILTER SORT] Vinted did not persist newest_first through UI. "
                        + "Applying URL fallback: {} -> {}",
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
                !waitForUrlParameterEquals(
                        "order",
                        "newest_first",
                        URL_PARAMETER_WAIT_MS
                )
        ) {

            throw new IllegalStateException(
                    "Could not apply newest_first sorting. URL: "
                            + page.url()
            );
        }


        log.info(
                "[FILTER SORT] newest_first persisted through URL fallback. "
                        + "Current URL: {}",
                page.url()
        );
    }


    private void applyPrice(
            BotDetailsDto bot
    ) {

        BotConfigurationDto configuration =
                bot.getConfiguration();


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


            /*
             * Vinted wcześniej zapisywało price_from,
             * ale czasami nie zatwierdzało price_to po samym
             * kliknięciu poza popupem.
             *
             * Enter na ostatnim polu wymusza zatwierdzenie formularza.
             */
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


        /*
         * Druga warstwa dla maxPrice.
         *
         * Jeśli UI Vinted nadal nie dopisało price_to,
         * zachowujemy wszystkie już ustawione parametry katalogu
         * i dopisujemy price_to bezpośrednio do bieżącego URL.
         */
        if (
                configuration.getMaxPrice()
                        != null
                        && !hasUrlParameter(
                        "price_to"
                )
        ) {

            String currentUrl =
                    page.url();


            String separator =
                    currentUrl.contains(
                            "?"
                    )
                            ? "&"
                            : "?";


            String correctedUrl =
                    currentUrl
                            + separator
                            + "price_to="
                            + configuration
                            .getMaxPrice()
                            .toPlainString();


            log.warn(
                    "[FILTER PRICE] Vinted did not persist maxPrice through "
                            + "the UI. Applying safe URL fallback: {} -> {}",
                    currentUrl,
                    correctedUrl
            );


            page.navigate(
                    correctedUrl
            );


            actions.waitForTimeout(
                    1_500
            );
        }


        if (
                configuration.getMaxPrice()
                        != null
                        && !hasUrlParameter(
                        "price_to"
                )
        ) {

            throw new IllegalStateException(
                    "Could not apply configured maxPrice to Vinted catalog URL"
            );
        }


        log.info(
                "[FILTER PRICE] Price filter completed. Current URL: {}",
                page.url()
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

            String value =
                    getUrlParameter(
                            parameterName
                    );


            if (
                    value != null
            ) {

                return true;
            }


            actions.waitForTimeout(
                    200
            );
        }


        return false;
    }


    private boolean waitForUrlParameterEquals(
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

            String value =
                    getUrlParameter(
                            parameterName
                    );


            if (
                    expectedValue.equals(
                            value
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


    private String withOrReplacedUrlParameter(
            String url,
            String parameterName,
            String parameterValue
    ) {

        String encodedPair =
                parameterName
                        + "="
                        + parameterValue;


        String parameterRegex =
                "([?&])"
                        + java.util.regex.Pattern.quote(
                        parameterName
                )
                        + "=[^&#]*";


        if (
                java.util.regex.Pattern
                        .compile(
                                parameterRegex
                        )
                        .matcher(
                                url
                        )
                        .find()
        ) {

            return url.replaceFirst(
                    parameterRegex,
                    "$1"
                            + encodedPair
            );
        }


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


        String separator =
                withoutFragment.contains(
                        "?"
                )
                        ? "&"
                        : "?";


        return withoutFragment
                + separator
                + encodedPair
                + fragment;
    }


    private String firstLineMessage(
            RuntimeException exception
    ) {

        if (exception == null) {

            return "Unknown error";
        }


        String message =
                exception.getMessage();


        if (
                message == null
                        || message.isBlank()
        ) {

            return exception
                    .getClass()
                    .getSimpleName();
        }


        int lineBreak =
                message.indexOf(
                        '\n'
                );


        if (
                lineBreak > 0
        ) {

            return message
                    .substring(
                            0,
                            lineBreak
                    )
                    .trim();
        }


        return message.trim();
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
                        && !hasAnyUrlParameter(
                        "catalog[]",
                        "catalog%5B%5D"
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
                        && !hasAnyUrlParameter(
                        "brand_ids[]",
                        "brand_ids%5B%5D"
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
                configuration.getMinPrice()
                        != null
        ) {

            String actualMinPrice =
                    getUrlParameter(
                            "price_from"
                    );


            if (
                    actualMinPrice == null
                            || actualMinPrice.isBlank()
            ) {

                throw new IllegalStateException(
                        "Final Vinted catalog URL does not contain price_from. "
                                + "Configured minPrice="
                                + configuration.getMinPrice()
                                + ". URL: "
                                + page.url()
                );
            }
        }


        if (
                configuration.getMaxPrice()
                        != null
        ) {

            String actualMaxPrice =
                    getUrlParameter(
                            "price_to"
                    );


            if (
                    actualMaxPrice == null
                            || actualMaxPrice.isBlank()
            ) {

                throw new IllegalStateException(
                        "Final Vinted catalog URL does not contain price_to. "
                                + "Configured maxPrice="
                                + configuration.getMaxPrice()
                                + ". URL: "
                                + page.url()
                );
            }
        }


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
                        + "brand='{}', minPrice={}, maxPrice={}, order={}.",
                targetMode,
                configuration.getSearchQuery(),
                configuration.getCategoryPath(),
                configuration.getBrand(),
                configuration.getMinPrice(),
                configuration.getMaxPrice(),
                order
        );
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


    private boolean hasAnyUrlParameter(
            String... parameterNames
    ) {

        String currentUrl =
                page.url();


        for (String parameterName : parameterNames) {

            if (
                    currentUrl.contains(
                            parameterName
                                    + "="
                    )
            ) {

                return true;
            }
        }


        return false;
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


    private boolean hasUrlParameter(
            String parameterName
    ) {

        String marker =
                parameterName
                        + "=";


        return page.url()
                .contains(
                        marker
                );
    }
}