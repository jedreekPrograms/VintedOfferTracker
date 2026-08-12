package pl.flipbot.playwright.filters;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.WaitUntilState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@RequiredArgsConstructor
public class FilterActions {

    private static final double FILTER_TIMEOUT_MS =
            10_000;

    private static final double OPTION_TIMEOUT_MS =
            10_000;

    private static final double RELOAD_TIMEOUT_MS =
            30_000;


    /*
     * ============================================================
     * BRAND RETRY
     * ============================================================
     */

    private static final int BRAND_MAX_ATTEMPTS =
            3;

    private static final double BRAND_PERSIST_TIMEOUT_MS =
            5_000;

    private static final double BRAND_RETRY_DELAY_MS =
            2_000;


    private final Page page;


    /*
     * Informacje o aktualnie otwartym filtrze.
     *
     * Potrzebujemy ich tylko po to,
     * żeby rozpoznać:
     *
     * BRAND_FILTER
     * -> selectOption(...)
     * -> FILTER_SELECTION
     *
     * i wtedy uruchomić specjalny retry marki.
     */
    private String activeFilterTestId;

    private String activeFilterBaseUrl;

    private String selectedBrandOption;


    public void openFilter(
            String filterTestId
    ) {

        /*
         * Zapamiętujemy URL PRZED rozpoczęciem
         * wyboru marki.
         *
         * W tym momencie dla SEARCH_QUERY
         * URL zawiera już np.:
         *
         * search_text=Galaxy Tab S11 Ultra
         * catalog[]=3728
         *
         * Jeżeli marka się nie zapisze,
         * wrócimy dokładnie tutaj.
         */
        if (
                FilterSelectors.BRAND_FILTER.equals(
                        filterTestId
                )
        ) {

            activeFilterBaseUrl =
                    page.url();

            selectedBrandOption =
                    null;
        }


        activeFilterTestId =
                filterTestId;


        Locator filter =
                page.getByTestId(
                        filterTestId
                );


        waitUntilVisible(
                filter,
                FILTER_TIMEOUT_MS
        );


        filter.click();
    }


    public void selectOption(
            String option
    ) {

        Locator locator =
                getOptionLocator(
                        option
                );


        waitUntilVisible(
                locator,
                OPTION_TIMEOUT_MS
        );


        locator.click();


        /*
         * Jeżeli aktualnie pracujemy
         * z filtrem marki,
         * zapamiętujemy wybraną markę.
         *
         * Będzie potrzebna w przypadku retry.
         */
        if (
                FilterSelectors.BRAND_FILTER.equals(
                        activeFilterTestId
                )
        ) {

            selectedBrandOption =
                    option;
        }
    }


    public void waitForOption(
            String option
    ) {

        Locator locator =
                getOptionLocator(
                        option
                );


        waitUntilVisible(
                locator,
                OPTION_TIMEOUT_MS
        );
    }


    public void fillInput(
            String testId,
            String value
    ) {

        Locator input =
                page.getByTestId(
                        testId
                );


        waitUntilVisible(
                input,
                OPTION_TIMEOUT_MS
        );


        input.fill(
                value
        );
    }


    public void pressEnter() {

        page.keyboard()
                .press(
                        "Enter"
                );
    }


    public void clickSelector(
            String selector
    ) {

        /*
         * Specjalny przypadek:
         *
         * FilterService:
         *
         * open BRAND
         * -> select Samsung
         * -> click FILTER_SELECTION
         *
         * Jeżeli dokładnie taki flow trwa,
         * potwierdzenie marki dostaje retry.
         */
        if (
                FilterSelectors.FILTER_SELECTION.equals(
                        selector
                )
                        && FilterSelectors.BRAND_FILTER.equals(
                        activeFilterTestId
                )
                        && selectedBrandOption != null
                        && !selectedBrandOption.isBlank()
        ) {

            confirmBrandWithRetry(
                    selector
            );


            clearActiveFilterState();


            return;
        }


        Locator locator =
                page.locator(
                        selector
                );


        waitUntilVisible(
                locator,
                OPTION_TIMEOUT_MS
        );


        locator.click();
    }


    /*
     * ============================================================
     * BRAND CONFIRM + RETRY
     * ============================================================
     */

    private void confirmBrandWithRetry(
            String confirmSelector
    ) {

        String brand =
                selectedBrandOption;


        String baseUrl =
                activeFilterBaseUrl;


        if (
                baseUrl == null
                        || baseUrl.isBlank()
        ) {

            throw new IllegalStateException(
                    "Cannot apply brand retry because the pre-brand catalog URL is missing"
            );
        }


        RuntimeException lastException =
                null;


        for (
                int attempt = 1;
                attempt <= BRAND_MAX_ATTEMPTS;
                attempt++
        ) {

            try {

                log.info(
                        "[FILTER BRAND] Attempt {}/{} for brand '{}'.",
                        attempt,
                        BRAND_MAX_ATTEMPTS,
                        brand
                );


                /*
                 * Pierwsza próba:
                 *
                 * marka została już zaznaczona
                 * przez FilterService.
                 *
                 * Musimy tylko kliknąć potwierdzenie.
                 *
                 *
                 * Kolejne próby:
                 *
                 * wróciliśmy do znanego dobrego URL,
                 * więc trzeba od nowa:
                 *
                 * open brand
                 * -> wait Samsung
                 * -> click Samsung
                 * -> confirm
                 */
                if (
                        attempt > 1
                ) {

                    prepareBrandRetry(
                            baseUrl,
                            brand
                    );
                }


                Locator confirmButton =
                        page.locator(
                                confirmSelector
                        );


                waitUntilVisible(
                        confirmButton,
                        OPTION_TIMEOUT_MS
                );


                confirmButton.click();


                /*
                 * Nie uznajemy kliknięcia za sukces.
                 *
                 * Sukces istnieje dopiero wtedy,
                 * kiedy URL naprawdę zawiera:
                 *
                 * brand_ids[]=...
                 */
                if (
                        waitForUrlParameterPresent(
                                "brand_ids[]",
                                BRAND_PERSIST_TIMEOUT_MS
                        )
                ) {

                    log.info(
                            "[FILTER BRAND] Brand '{}' persisted successfully "
                                    + "on attempt {}/{}. brand_ids[]={}.",
                            brand,
                            attempt,
                            BRAND_MAX_ATTEMPTS,
                            getUrlParameter(
                                    "brand_ids[]"
                            )
                    );


                    return;
                }


                log.warn(
                        "[FILTER BRAND] Attempt {}/{} for '{}' finished, "
                                + "but Vinted did not persist brand_ids[]. "
                                + "Current URL: {}",
                        attempt,
                        BRAND_MAX_ATTEMPTS,
                        brand,
                        page.url()
                );


            } catch (RuntimeException exception) {

                lastException =
                        exception;


                log.warn(
                        "[FILTER BRAND] Attempt {}/{} for '{}' failed: {}",
                        attempt,
                        BRAND_MAX_ATTEMPTS,
                        brand,
                        getFriendlyErrorMessage(
                                exception
                        )
                );


                log.trace(
                        "[FILTER BRAND] Full brand filter error. Attempt {}/{}.",
                        attempt,
                        BRAND_MAX_ATTEMPTS,
                        exception
                );
            }


            if (
                    attempt < BRAND_MAX_ATTEMPTS
            ) {

                /*
                 * Bardzo ważne:
                 *
                 * nie reloadujemy przypadkowego,
                 * częściowo zmienionego URL.
                 *
                 * Wracamy do URL zapisanego
                 * PRZED pierwszą próbą marki.
                 *
                 * Dzięki temu zachowujemy np.:
                 *
                 * SEARCH_QUERY:
                 * search_text
                 * catalog[]
                 *
                 * VINTED_MODEL:
                 * catalog[]
                 */
                resetToBrandBaseUrl(
                        baseUrl,
                        attempt
                );


                log.info(
                        "[FILTER BRAND] Next attempt in {}ms.",
                        (int) BRAND_RETRY_DELAY_MS
                );


                page.waitForTimeout(
                        BRAND_RETRY_DELAY_MS
                );
            }
        }


        String message =
                "Could not persist brand filter '"
                        + brand
                        + "' after "
                        + BRAND_MAX_ATTEMPTS
                        + " attempts. "
                        + "Expected URL parameter brand_ids[]. "
                        + "Base URL: "
                        + baseUrl
                        + ". Current URL: "
                        + page.url();


        if (
                lastException != null
        ) {

            throw new IllegalStateException(
                    message,
                    lastException
            );
        }


        throw new IllegalStateException(
                message
        );
    }


    private void prepareBrandRetry(
            String baseUrl,
            String brand
    ) {

        /*
         * Powinniśmy już być na baseUrl,
         * bo reset został wykonany
         * po poprzedniej próbie.
         *
         * Dodatkowa kontrola chroni nas
         * przed przypadkowym kontynuowaniem
         * na stronie przedmiotu / innym URL.
         */
        if (
                !page.url().equals(
                        baseUrl
                )
        ) {

            navigateToBrandBaseUrl(
                    baseUrl
            );
        }


        Locator brandFilter =
                page.getByTestId(
                        FilterSelectors.BRAND_FILTER
                );


        waitUntilVisible(
                brandFilter,
                FILTER_TIMEOUT_MS
        );


        brandFilter.click();


        Locator brandOption =
                getOptionLocator(
                        brand
                );


        waitUntilVisible(
                brandOption,
                OPTION_TIMEOUT_MS
        );


        brandOption.click();


        log.info(
                "[FILTER BRAND] Brand '{}' selected again for retry.",
                brand
        );
    }


    private void resetToBrandBaseUrl(
            String baseUrl,
            int failedAttempt
    ) {

        log.info(
                "[FILTER BRAND] Resetting catalog to the known-good "
                        + "pre-brand URL after failed attempt {}/{}.",
                failedAttempt,
                BRAND_MAX_ATTEMPTS
        );


        navigateToBrandBaseUrl(
                baseUrl
        );


        /*
         * Kontrola bezpieczeństwa:
         *
         * po nawigacji URL powinien być
         * dokładnie tym stanem, który mieliśmy
         * przed marką.
         */
        if (
                !page.url().equals(
                        baseUrl
                )
        ) {

            log.warn(
                    "[FILTER BRAND] Browser URL after reset differs from "
                            + "the stored pre-brand URL. "
                            + "Expected: {}. Actual: {}.",
                    baseUrl,
                    page.url()
            );
        }


        log.info(
                "[FILTER BRAND] Pre-brand catalog state restored."
        );
    }


    private void navigateToBrandBaseUrl(
            String baseUrl
    ) {

        page.navigate(
                baseUrl,
                new Page.NavigateOptions()
                        .setWaitUntil(
                                WaitUntilState.DOMCONTENTLOADED
                        )
                        .setTimeout(
                                RELOAD_TIMEOUT_MS
                        )
        );
    }


    /*
     * ============================================================
     * STANDARD ACTIONS
     * ============================================================
     */

    public void fillInputBySelector(
            String selector,
            Object value
    ) {

        Locator input =
                page.locator(
                        selector
                );


        waitUntilVisible(
                input,
                OPTION_TIMEOUT_MS
        );


        input.fill(
                String.valueOf(
                        value
                )
        );
    }


    public void clickModel(
            String model
    ) {

        Locator modelLocator =
                page.locator(
                                "[data-testid^='selectable-item-brand_collection-']"
                        )
                        .filter(
                                new Locator.FilterOptions()
                                        .setHasText(
                                                model
                                        )
                        )
                        .first();


        waitUntilVisible(
                modelLocator,
                OPTION_TIMEOUT_MS
        );


        modelLocator.click();
    }


    public void clickConfirmButton() {

        Locator button =
                page.getByTestId(
                        "filter-selection-button"
                );


        waitUntilVisible(
                button,
                OPTION_TIMEOUT_MS
        );


        button.click();
    }


    public void clickOutsideSafely() {

        page.evaluate(
                """
                () => {
                    const target = document.body;

                    const eventOptions = {
                        bubbles: true,
                        cancelable: true,
                        view: window
                    };

                    target.dispatchEvent(
                        new MouseEvent(
                            "mousedown",
                            eventOptions
                        )
                    );

                    target.dispatchEvent(
                        new MouseEvent(
                            "mouseup",
                            eventOptions
                        )
                    );

                    target.dispatchEvent(
                        new MouseEvent(
                            "click",
                            eventOptions
                        )
                    );
                }
                """
        );


        page.waitForTimeout(
                1_000
        );
    }


    /*
     * Używane przez CategoryNavigator.
     *
     * Zachowujemy poprawkę z poprzedniego etapu:
     * reload dokładnie bieżącego URL.
     */
    public void reloadCurrentPage() {

        page.reload(
                new Page.ReloadOptions()
                        .setWaitUntil(
                                WaitUntilState.DOMCONTENTLOADED
                        )
                        .setTimeout(
                                RELOAD_TIMEOUT_MS
                        )
        );
    }


    public void waitForTimeout(
            double milliseconds
    ) {

        page.waitForTimeout(
                milliseconds
        );
    }


    /*
     * ============================================================
     * URL HELPERS
     * ============================================================
     */

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
                    getUrlParameter(
                            parameterName
                    ) != null
            ) {

                return true;
            }


            page.waitForTimeout(
                    200
            );
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


    /*
     * ============================================================
     * INTERNAL HELPERS
     * ============================================================
     */

    private Locator getOptionLocator(
            String option
    ) {

        return page.getByRole(
                AriaRole.BUTTON,
                new Page.GetByRoleOptions()
                        .setName(
                                option
                        )
        );
    }


    private void waitUntilVisible(
            Locator locator,
            double timeoutMilliseconds
    ) {

        locator.waitFor(
                new Locator.WaitForOptions()
                        .setState(
                                WaitForSelectorState.VISIBLE
                        )
                        .setTimeout(
                                timeoutMilliseconds
                        )
        );
    }


    private void clearActiveFilterState() {

        activeFilterTestId =
                null;

        activeFilterBaseUrl =
                null;

        selectedBrandOption =
                null;
    }


    private String getFriendlyErrorMessage(
            Throwable exception
    ) {

        if (
                exception == null
        ) {

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


        int firstLineEnd =
                message.indexOf(
                        '\n'
                );


        if (
                firstLineEnd > 0
        ) {

            return message
                    .substring(
                            0,
                            firstLineEnd
                    )
                    .trim();
        }


        return message.trim();
    }
}