package pl.flipbot.playwright.filters;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class FilterActions {

    private final Page page;


    public void openFilter(
            String filterTestId
    ) {

        Locator filter =
                page.getByTestId(
                        filterTestId
                );


        /*
         * Wracamy do mechaniki, która realnie działała wcześniej.
         *
         * Nie dokładamy tutaj:
         * - exact accessible-name,
         * - .first(),
         * - Escape przed kliknięciem,
         * - sztucznego przełączania overlay.
         *
         * Vinted samo renderuje i przełącza kolejne poziomy filtra.
         */
        filter.waitFor();
        filter.click();
    }


    public void selectOption(
            String option
    ) {

        Locator locator =
                page.getByRole(
                        AriaRole.BUTTON,
                        new Page.GetByRoleOptions()
                                .setName(
                                        option
                                )
                );


        locator.waitFor();
        locator.click();
    }


    public void waitForOption(
            String option
    ) {

        page.getByRole(
                        AriaRole.BUTTON,
                        new Page.GetByRoleOptions()
                                .setName(
                                        option
                                )
                )
                .waitFor(
                        new Locator.WaitForOptions()
                                .setState(
                                        WaitForSelectorState.VISIBLE
                                )
                                .setTimeout(
                                        5_000
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


        input.waitFor();
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

        page.locator(
                        selector
                )
                .click();
    }


    public void fillInputBySelector(
            String selector,
            Object value
    ) {

        Locator input =
                page.locator(
                        selector
                );


        input.waitFor();

        input.fill(
                String.valueOf(
                        value
                )
        );


        input.waitFor();
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


        modelLocator.waitFor();
        modelLocator.click();
    }


    public void clickConfirmButton() {

        Locator button =
                page.getByTestId(
                        "filter-selection-button"
                );


        button.waitFor();
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


    public void waitForTimeout(
            double milliseconds
    ) {

        page.waitForTimeout(
                milliseconds
        );
    }
}