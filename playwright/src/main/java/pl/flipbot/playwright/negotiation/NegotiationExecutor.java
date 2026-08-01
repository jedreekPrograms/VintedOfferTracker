package pl.flipbot.playwright.negotiation;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.WaitUntilState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.scanner.model.Listing;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class NegotiationExecutor {

    private static final double NAVIGATION_TIMEOUT_MS =
            30_000;

    private static final double ELEMENT_WAIT_TIMEOUT_MS =
            10_000;

    private static final double PAGE_STABILIZATION_DELAY_MS =
            2_000;

    private static final int MAX_ELEMENTS_TO_LOG =
            150;

    private final BotContext context;

    public void inspectFirstListing(
            List<Listing> listings
    ) {

        if (listings == null
                || listings.isEmpty()) {

            log.info(
                    "Bot {} has no listings available for inspection",
                    context.getBot().getId()
            );

            return;

        }

        Listing listing =
                listings.getFirst();

        if (!isValid(listing)) {

            log.warn(
                    "First listing cannot be inspected because its data is incomplete: "
                            + "id={}, url={}",
                    listing == null
                            ? null
                            : listing.getId(),
                    listing == null
                            ? null
                            : listing.getUrl()
            );

            return;

        }

        try {

            openListing(listing);

            inspectInteractiveElements();

        } catch (RuntimeException exception) {

            log.error(
                    "Could not inspect listing {} at URL {}",
                    listing.getId(),
                    listing.getUrl(),
                    exception
            );

        }

    }

    private void openListing(
            Listing listing
    ) {

        Page page =
                context.getPage();

        log.info(
                "Opening listing {} for page inspection: {}",
                listing.getId(),
                listing.getUrl()
        );

        page.navigate(
                listing.getUrl(),
                new Page.NavigateOptions()
                        .setWaitUntil(
                                WaitUntilState.DOMCONTENTLOADED
                        )
                        .setTimeout(
                                NAVIGATION_TIMEOUT_MS
                        )
        );

        page.locator("body")
                .waitFor(
                        new Locator.WaitForOptions()
                                .setState(
                                        WaitForSelectorState.ATTACHED
                                )
                                .setTimeout(
                                        ELEMENT_WAIT_TIMEOUT_MS
                                )
                );

        /*
         * DOMContentLoaded oznacza, że podstawowy HTML został załadowany.
         * Vinted może jednak doładowywać elementy przez JavaScript,
         * dlatego dajemy stronie jeszcze dwie sekundy.
         */
        page.waitForTimeout(
                PAGE_STABILIZATION_DELAY_MS
        );

        log.info(
                "Listing {} opened. Current URL: {}, page title: {}",
                listing.getId(),
                page.url(),
                page.title()
        );

    }

    private void inspectInteractiveElements() {

        Page page =
                context.getPage();

        Locator elements =
                page.locator(
                        "button, a, [role='button']"
                );

        int totalElements =
                elements.count();

        int inspectedElements =
                Math.min(
                        totalElements,
                        MAX_ELEMENTS_TO_LOG
                );

        log.info(
                "Found {} interactive elements. Inspecting first {} elements",
                totalElements,
                inspectedElements
        );

        for (int index = 0;
             index < inspectedElements;
             index++) {

            Locator element =
                    elements.nth(index);

            String text =
                    readText(element);

            String dataTestId =
                    readAttribute(
                            element,
                            "data-testid"
                    );

            String ariaLabel =
                    readAttribute(
                            element,
                            "aria-label"
                    );

            String href =
                    readAttribute(
                            element,
                            "href"
                    );

            String role =
                    readAttribute(
                            element,
                            "role"
                    );

            if (allBlank(
                    text,
                    dataTestId,
                    ariaLabel,
                    href,
                    role
            )) {

                continue;

            }

            log.info(
                    "Interactive element {}: "
                            + "text='{}', "
                            + "data-testid='{}', "
                            + "aria-label='{}', "
                            + "role='{}', "
                            + "href='{}'",
                    index,
                    text,
                    dataTestId,
                    ariaLabel,
                    role,
                    href
            );

        }

        if (totalElements > MAX_ELEMENTS_TO_LOG) {

            log.info(
                    "Skipped logging {} remaining interactive elements",
                    totalElements - MAX_ELEMENTS_TO_LOG
            );

        }

    }

    private String readText(
            Locator element
    ) {

        try {

            String text =
                    element.textContent();

            if (text == null) {
                return null;
            }

            return normalizeWhitespace(
                    text
            );

        } catch (PlaywrightException exception) {

            return null;

        }

    }

    private String readAttribute(
            Locator element,
            String attributeName
    ) {

        try {

            return element.getAttribute(
                    attributeName
            );

        } catch (PlaywrightException exception) {

            return null;

        }

    }

    private String normalizeWhitespace(
            String value
    ) {

        return value
                .replaceAll(
                        "\\s+",
                        " "
                )
                .trim();

    }

    private boolean allBlank(
            String... values
    ) {

        for (String value : values) {

            if (value != null
                    && !value.isBlank()) {

                return false;

            }

        }

        return true;

    }

    private boolean isValid(
            Listing listing
    ) {

        return listing != null
                && isNotBlank(
                listing.getId()
        )
                && isNotBlank(
                listing.getUrl()
        );

    }

    private boolean isNotBlank(
            String value
    ) {

        return value != null
                && !value.isBlank();

    }

}