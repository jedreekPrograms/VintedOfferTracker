package pl.flipbot.playwright.filters;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import static pl.flipbot.playwright.filters.VintedLocatorSafety.safeCount;
import static pl.flipbot.playwright.filters.VintedLocatorSafety.safeIsVisible;
import static pl.flipbot.playwright.filters.VintedLocatorSafety.safeAttribute;
import static pl.flipbot.playwright.filters.VintedLocatorSafety.getFriendlyErrorMessage;
import static pl.flipbot.playwright.filters.VintedModelOptionIdentity.canonicalModelRowTestId;

/**
 * Operates only on the exact native Vinted model row whose ID was proven from
 * visible title evidence. The discovery/retry and URL persistence state machine
 * remains with FilterActions. No fuzzy matching, backend writes or offers.
 *
 * Existing bounded click fallback order is retained pending live DOM testing.
 */
@Slf4j
final class VintedModelRowInteractor {
    private final Page page;

    VintedModelRowInteractor(Page page) {
        this.page = page;
    }

    Locator canonicalModelRow(
            String collectionId,
            Locator evidenceLocator
    ) {
        String canonicalTestId = canonicalModelRowTestId(collectionId);

        /*
         * clickModel() now searches only canonical role=button rows. Prefer the
         * exact row that supplied the matching text instead of resolving a
         * page-wide .first() which can point at a stale/responsive duplicate.
         */
        if (canonicalTestId.equals(safeAttribute(evidenceLocator, "data-testid"))
                && safeIsVisible(evidenceLocator)) {
            return evidenceLocator;
        }

        Locator canonicalRows = page.getByTestId(canonicalTestId);
        int rowCount = safeCount(canonicalRows);
        for (int index = 0; index < rowCount; index++) {
            Locator candidate = canonicalRows.nth(index);
            if (safeIsVisible(candidate)) {
                return candidate;
            }
        }

        /*
         * Compatibility fallback for a future Vinted variant where exact text
         * evidence comes from a child instead of the canonical row itself.
         */
        Locator ancestorCandidate = evidenceLocator.locator(
                "xpath=ancestor::*[@role='button' and @data-testid='" + canonicalTestId + "'][1]"
        );

        if (safeCount(ancestorCandidate) > 0
                && safeIsVisible(ancestorCandidate.first())) {
            return ancestorCandidate.first();
        }

        return evidenceLocator;
    }

    void selectExactModelRow(
            Locator modelRow,
            String model,
            String collectionId
    ) {
        if (isExactModelSelected(modelRow, collectionId)) {
            log.info(
                    "[FILTER MODEL] Exact model '{}' / collectionId={} is already selected.",
                    model,
                    collectionId
            );
            return;
        }

        Locator nativeCheckbox = modelRow.locator(
                exactModelCheckboxSelector(collectionId)
        ).first();
        boolean hasNativeCheckbox = safeCount(nativeCheckbox) > 0;
        RuntimeException primaryFailure = null;

        log.info(
                "[FILTER MODEL] Selecting exact model '{}' / collectionId={}. rowTestId='{}', nativeCheckboxPresent={}, checkboxAriaLabel='{}'.",
                model,
                collectionId,
                safeAttribute(modelRow, "data-testid"),
                hasNativeCheckbox,
                hasNativeCheckbox
                        ? safeAttribute(nativeCheckbox, "aria-label")
                        : ""
        );

        /*
         * The exact right-hand suffix is the real click area observed in the
         * live Vinted DOM:
         *
         *   selectable-item-brand_collection-<ID>--suffix
         *
         * Resolve it INSIDE the already-proven row. This avoids stale duplicate
         * controls and keeps S25 / S25 FE / Ultra / Edge completely isolated.
         */
        Locator modelSuffix = modelRow.locator(
                "[data-testid='" + exactModelSuffixTestId(collectionId) + "']"
        ).first();

        if (safeCount(modelSuffix) > 0 && safeIsVisible(modelSuffix)) {
            try {
                log.info(
                        "[FILTER MODEL] Clicking exact model suffix '{}' for '{}' / collectionId={}.",
                        exactModelSuffixTestId(collectionId),
                        model,
                        collectionId
                );

                modelSuffix.click(
                        new Locator.ClickOptions()
                                .setTimeout(2_000)
                );

                if (waitForExactModelSelected(
                        modelRow,
                        collectionId,
                        1_500
                )) {
                    log.info(
                            "[FILTER MODEL] Exact model '{}' / collectionId={} selected by exact suffix Playwright click.",
                            model,
                            collectionId
                    );
                    return;
                }

                /*
                 * Some Vinted builds put pointer-events:none on the checkbox
                 * subtree while the parent Cell owns the handler. HTMLElement
                 * click on the exact suffix bubbles to that Cell without fuzzy
                 * coordinates or another model row.
                 */
                modelSuffix.evaluate("element => element.click()");

                if (waitForExactModelSelected(
                        modelRow,
                        collectionId,
                        1_500
                )) {
                    log.info(
                            "[FILTER MODEL] Exact model '{}' / collectionId={} selected by exact suffix DOM click.",
                            model,
                            collectionId
                    );
                    return;
                }

                /*
                 * Last suffix-specific pointer path: click the visual center of
                 * THIS exact suffix with Chromium's mouse. This reproduces the
                 * manual click while still deriving the target from collectionId.
                 */
                modelSuffix.scrollIntoViewIfNeeded();
                var suffixBox = modelSuffix.boundingBox();

                if (suffixBox != null
                        && suffixBox.width > 0
                        && suffixBox.height > 0) {
                    double clickX = suffixBox.x + (suffixBox.width / 2.0);
                    double clickY = suffixBox.y + (suffixBox.height / 2.0);

                    page.mouse().click(clickX, clickY);

                    if (waitForExactModelSelected(
                            modelRow,
                            collectionId,
                            1_500
                    )) {
                        log.info(
                                "[FILTER MODEL] Exact model '{}' / collectionId={} selected by physical exact-suffix click.",
                                model,
                                collectionId
                        );
                        return;
                    }
                }

                log.warn(
                        "[FILTER MODEL] All exact-suffix click paths for '{}' / collectionId={} left the checkbox unchecked. Trying bounded compatibility fallbacks.",
                        model,
                        collectionId
                );
            } catch (RuntimeException exception) {
                primaryFailure = exception;
                log.warn(
                        "[FILTER MODEL] Exact suffix activation failed for '{}' / collectionId={}: {}. Trying bounded compatibility fallbacks.",
                        model,
                        collectionId,
                        getFriendlyErrorMessage(exception)
                );
            }
        } else {
            log.warn(
                    "[FILTER MODEL] Exact suffix '{}' is missing or not visible inside canonical row for '{}' / collectionId={}. Trying bounded compatibility fallbacks.",
                    exactModelSuffixTestId(collectionId),
                    model,
                    collectionId
            );
        }

        /*
         * Keep a short normal Playwright row click as the next compatibility
         * path. The explicit timeout prevents an actionability/interception
         * wait from stalling the whole worker for the default Playwright timeout.
         */
        try {
            modelRow.click(
                    new Locator.ClickOptions()
                            .setTimeout(2_000)
            );

            if (waitForExactModelSelected(modelRow, collectionId, 1_500)) {
                log.info(
                        "[FILTER MODEL] Exact model '{}' / collectionId={} selected by canonical row click.",
                        model,
                        collectionId
                );
                return;
            }
        } catch (RuntimeException exception) {
            if (primaryFailure == null) {
                primaryFailure = exception;
            }
            log.debug(
                    "[FILTER MODEL] Canonical row click failed for '{}' / collectionId={}.",
                    model,
                    collectionId,
                    exception
            );
        }

        /*
         * If browser actionability is the blocker, invoke the canonical Cell's
         * click handler directly. This targets the exact proven row, not the
         * text node and not a fuzzy model candidate.
         */
        try {
            modelRow.evaluate("element => element.click()");

            if (waitForExactModelSelected(modelRow, collectionId, 1_500)) {
                log.info(
                        "[FILTER MODEL] Exact model '{}' / collectionId={} selected by canonical row DOM activation.",
                        model,
                        collectionId
                );
                return;
            }
        } catch (RuntimeException exception) {
            log.debug(
                    "[FILTER MODEL] Canonical row DOM activation failed for '{}' / collectionId={}.",
                    model,
                    collectionId,
                    exception
            );
        }

        for (String key : List.of("Enter", "Space")) {
            try {
                modelRow.press(key);

                if (waitForExactModelSelected(
                        modelRow,
                        collectionId,
                        1_000
                )) {
                    log.info(
                            "[FILTER MODEL] Exact model '{}' / collectionId={} selected by row keyboard activation '{}'.",
                            model,
                            collectionId,
                            key
                    );
                    return;
                }
            } catch (RuntimeException exception) {
                log.debug(
                        "[FILTER MODEL] Row keyboard activation '{}' failed for '{}' / collectionId={}.",
                        key,
                        model,
                        collectionId,
                        exception
                );
            }
        }

        /*
         * Final state-specific fallback. This exact input is bound to the
         * already-proven collection id, so it cannot select S25/Ultra/Edge when
         * the requested model is S25 FE.
         */
        if (hasNativeCheckbox) {
            try {
                nativeCheckbox.evaluate("element => element.click()");

                if (waitForExactModelSelected(
                        modelRow,
                        collectionId,
                        1_500
                )) {
                    log.info(
                            "[FILTER MODEL] Exact model '{}' / collectionId={} selected by exact checkbox DOM fallback.",
                            model,
                            collectionId
                    );
                    return;
                }
            } catch (RuntimeException exception) {
                log.debug(
                        "[FILTER MODEL] Exact checkbox DOM fallback failed for '{}' / collectionId={}.",
                        model,
                        collectionId,
                        exception
                );
            }
        }

        String message =
                "Exact Vinted model row was found, but its exact checkbox never became selected. Model='"
                        + model
                        + "', collectionId="
                        + collectionId
                        + ", rowTestId='"
                        + safeAttribute(modelRow, "data-testid")
                        + "', checkboxPresent="
                        + hasNativeCheckbox
                        + ", checkboxAriaLabel='"
                        + (hasNativeCheckbox
                        ? safeAttribute(nativeCheckbox, "aria-label")
                        : "")
                        + "'.";

        if (primaryFailure != null) {
            throw new IllegalStateException(message, primaryFailure);
        }

        throw new IllegalStateException(message);
    }

    static String exactModelSuffixTestId(String collectionId) {
        if (collectionId == null
                || !collectionId.matches("^\\d+$")) {
            throw new IllegalArgumentException(
                    "Model collection id must contain digits only"
            );
        }

        return canonicalModelRowTestId(collectionId) + "--suffix";
    }

    static String exactModelCheckboxSelector(String collectionId) {
        if (collectionId == null
                || !collectionId.matches("^\\d+$")) {
            throw new IllegalArgumentException(
                    "Model collection id must contain digits only"
            );
        }

        return "input[type='checkbox'][name='brand_collection_ids[]'][value='"
                + collectionId
                + "']";
    }

    private boolean waitForExactModelSelected(
            Locator modelRow,
            String collectionId,
            double timeoutMilliseconds
    ) {
        long deadline =
                System.currentTimeMillis()
                        + (long) timeoutMilliseconds;

        while (System.currentTimeMillis() <= deadline) {
            if (isExactModelSelected(modelRow, collectionId)) {
                return true;
            }

            page.waitForTimeout(100);
        }

        return false;
    }

    private boolean isExactModelSelected(
            Locator modelRow,
            String collectionId
    ) {
        try {
            Locator exactCheckbox = modelRow.locator(
                    exactModelCheckboxSelector(collectionId)
            ).first();

            if (exactCheckbox.count() > 0) {
                /*
                 * When the exact native checkbox exists, it is the authoritative
                 * state oracle. Do not accept a generic row/role state that could
                 * describe another custom control in the same Cell.
                 */
                return exactCheckbox.isChecked();
            }
        } catch (RuntimeException ignored) {
            // Fall through only for DOM variants that do not expose the input.
        }

        try {
            Locator roleCheckbox =
                    modelRow.getByRole(AriaRole.CHECKBOX).first();

            if (roleCheckbox.count() > 0) {
                String ariaChecked =
                        roleCheckbox.getAttribute("aria-checked");

                if ("true".equalsIgnoreCase(ariaChecked)) {
                    return true;
                }

                try {
                    if (roleCheckbox.isChecked()) {
                        return true;
                    }
                } catch (RuntimeException ignored) {
                    // Custom role=checkbox is not necessarily an <input>.
                }
            }
        } catch (RuntimeException ignored) {
            // Try row/custom attributes below.
        }

        String rowAriaChecked =
                safeAttribute(modelRow, "aria-checked");
        if ("true".equalsIgnoreCase(rowAriaChecked)) {
            return true;
        }

        String rowAriaSelected =
                safeAttribute(modelRow, "aria-selected");
        if ("true".equalsIgnoreCase(rowAriaSelected)) {
            return true;
        }

        String rowDataState =
                safeAttribute(modelRow, "data-state");
        return "checked".equalsIgnoreCase(rowDataState)
                || "selected".equalsIgnoreCase(rowDataState);
    }
}
