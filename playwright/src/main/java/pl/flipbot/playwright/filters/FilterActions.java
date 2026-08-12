package pl.flipbot.playwright.filters;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class FilterActions {

    private static final double FILTER_TRIGGER_TIMEOUT_MS =
            10_000;

    private static final double FILTER_OPTION_TIMEOUT_MS =
            8_000;

    private static final double UI_SETTLE_DELAY_MS =
            250;


    private final Page page;


    public void openFilter(
            String filterTestId
    ) {

        Locator filter =
                page.getByTestId(
                        filterTestId
                );


        filter.waitFor(
                new Locator.WaitForOptions()
                        .setState(
                                WaitForSelectorState.VISIBLE
                        )
                        .setTimeout(
                                FILTER_TRIGGER_TIMEOUT_MS
                        )
        );


        filter.click();


        /*
         * Vinted renderuje zawartość popupu asynchronicznie.
         * Krótka pauza zmniejsza ryzyko, że od razu zaczniemy
         * szukać opcji w poprzednim / niedorenderowanym stanie.
         */
        page.waitForTimeout(
                UI_SETTLE_DELAY_MS
        );
    }


    public void selectOption(
            String option
    ) {

        Locator locator =
                getExactOption(
                        option
                );


        locator.waitFor(
                new Locator.WaitForOptions()
                        .setState(
                                WaitForSelectorState.VISIBLE
                        )
                        .setTimeout(
                                FILTER_OPTION_TIMEOUT_MS
                        )
        );


        locator.click();


        /*
         * Po wejściu o poziom niżej Vinted musi wyrenderować
         * kolejną listę kategorii.
         */
        page.waitForTimeout(
                UI_SETTLE_DELAY_MS
        );
    }


    public void waitForOption(
            String option
    ) {

        getExactOption(
                option
        )
                .waitFor(
                        new Locator.WaitForOptions()
                                .setState(
                                        WaitForSelectorState.VISIBLE
                                )
                                .setTimeout(
                                        FILTER_OPTION_TIMEOUT_MS
                                )
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


        input.waitFor(
                new Locator.WaitForOptions()
                        .setState(
                                WaitForSelectorState.VISIBLE
                        )
                        .setTimeout(
                                FILTER_TRIGGER_TIMEOUT_MS
                        )
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

        Locator locator =
                page.locator(
                                selector
                        )
                        .first();


        locator.waitFor(
                new Locator.WaitForOptions()
                        .setState(
                                WaitForSelectorState.VISIBLE
                        )
                        .setTimeout(
                                FILTER_TRIGGER_TIMEOUT_MS
                        )
        );


        locator.click();
    }


    public void fillInputBySelector(
            String selector,
            Object value
    ) {

        Locator input =
                page.locator(
                                selector
                        )
                        .first();


        input.waitFor(
                new Locator.WaitForOptions()
                        .setState(
                                WaitForSelectorState.VISIBLE
                        )
                        .setTimeout(
                                FILTER_TRIGGER_TIMEOUT_MS
                        )
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


        modelLocator.waitFor(
                new Locator.WaitForOptions()
                        .setState(
                                WaitForSelectorState.VISIBLE
                        )
                        .setTimeout(
                                FILTER_OPTION_TIMEOUT_MS
                        )
        );


        modelLocator.click();


        page.waitForTimeout(
                UI_SETTLE_DELAY_MS
        );
    }


    public void clickConfirmButton() {

        Locator button =
                page.getByTestId(
                                "filter-selection-button"
                        )
                        .first();


        button.waitFor(
                new Locator.WaitForOptions()
                        .setState(
                                WaitForSelectorState.VISIBLE
                        )
                        .setTimeout(
                                FILTER_TRIGGER_TIMEOUT_MS
                        )
        );


        button.click();


        page.waitForTimeout(
                UI_SETTLE_DELAY_MS
        );
    }


    /*
     * Resetuje popup / dropdown przed ponowną próbą.
     *
     * To jest ważne dla CategoryNavigator:
     * jeżeli próba skończyła się np. na drugim poziomie kategorii,
     * kolejny openFilter() nie może pracować na pozostawionym
     * częściowo otwartym stanie.
     *
     * Escape na zamkniętym popupie jest bezpieczny.
     */
    public void dismissOpenOverlaySafely() {

        try {

            page.keyboard()
                    .press(
                            "Escape"
                    );


            page.waitForTimeout(
                    UI_SETTLE_DELAY_MS
            );

        } catch (RuntimeException ignored) {

            // Best-effort cleanup przed retry.
        }
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


    public void waitForTimeout(
            double milliseconds
    ) {

        page.waitForTimeout(
                milliseconds
        );
    }


    private Locator getExactOption(
            String option
    ) {

        return page.getByRole(
                        AriaRole.BUTTON,
                        new Page.GetByRoleOptions()
                                .setName(
                                        option
                                )
                                .setExact(
                                        true
                                )
                )
                .first();
    }
}