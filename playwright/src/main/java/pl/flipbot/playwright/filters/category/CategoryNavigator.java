package pl.flipbot.playwright.filters.category;

import com.microsoft.playwright.TimeoutError;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.filters.FilterActions;
import pl.flipbot.playwright.filters.FilterSelectors;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class CategoryNavigator {

    private static final int MAX_ATTEMPTS = 3;
    private static final double RETRY_DELAY_MS = 2_000;
    private static final double FIRST_ROOT_OPTION_TIMEOUT_MS = 10_000;
    private static final double STANDARD_OPTION_TIMEOUT_MS = 10_000;
    private static final double CATEGORY_PERSIST_TIMEOUT_MS = 5_000;

    private final FilterActions actions;

    public void select(List<String> categoryPath) {
        if (categoryPath == null || categoryPath.isEmpty()) {
            log.debug("[FILTER CATEGORY] No category path configured. Skipping.");
            return;
        }

        RuntimeException lastException = null;
        String failedCategory = null;
        double failedTimeoutMs = STANDARD_OPTION_TIMEOUT_MS;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                log.info(
                        "[FILTER CATEGORY] Attempt {}/{} started. Path: {}",
                        attempt,
                        MAX_ATTEMPTS,
                        formatPath(categoryPath)
                );

                selectCategoryPath(categoryPath, attempt);

                if (!actions.waitForUrlParameterPresent(
                        "catalog[]",
                        CATEGORY_PERSIST_TIMEOUT_MS
                )) {
                    String leafCategory = categoryPath.getLast();
                    throw new CategorySelectionException(
                            leafCategory,
                            CATEGORY_PERSIST_TIMEOUT_MS,
                            new IllegalStateException(
                                    "Vinted accepted the category clicks but did not persist catalog[] in the URL"
                            )
                    );
                }

                log.info(
                        "[FILTER CATEGORY] Category selected and URL persistence verified: {}",
                        formatPath(categoryPath)
                );
                return;

            } catch (CategorySelectionException exception) {
                lastException = exception;
                failedCategory = exception.getCategory();
                failedTimeoutMs = exception.getTimeoutMilliseconds();

                logSelectionFailure(
                        attempt,
                        exception.getCategory(),
                        exception.getCause(),
                        failedTimeoutMs
                );

                if (attempt < MAX_ATTEMPTS) {
                    resetBeforeRetry(attempt);
                }

            } catch (RuntimeException exception) {
                lastException = exception;

                if (attempt < MAX_ATTEMPTS) {
                    log.info(
                            "[FILTER CATEGORY] Attempt {}/{} needs retry because of: {}",
                            attempt,
                            MAX_ATTEMPTS,
                            getFriendlyErrorMessage(exception)
                    );
                } else {
                    log.warn(
                            "[FILTER CATEGORY] Final attempt {}/{} failed because of: {}",
                            attempt,
                            MAX_ATTEMPTS,
                            getFriendlyErrorMessage(exception)
                    );
                }

                log.trace(
                        "[FILTER CATEGORY] Full unexpected error for attempt {}/{}.",
                        attempt,
                        MAX_ATTEMPTS,
                        exception
                );

                if (attempt < MAX_ATTEMPTS) {
                    resetBeforeRetry(attempt);
                }
            }
        }

        StringBuilder errorMessage = new StringBuilder(
                "Could not select and persist category path after "
                        + MAX_ATTEMPTS
                        + " attempts: "
                        + formatPath(categoryPath)
        );

        if (failedCategory != null) {
            errorMessage.append(". Last failed category: ").append(failedCategory);
            errorMessage.append(". Last option/persistence timeout: ")
                    .append(Math.round(failedTimeoutMs / 1_000))
                    .append("s");
        }

        if (lastException != null) {
            errorMessage.append(". Last error: ")
                    .append(getFriendlyErrorMessage(lastException));
        }

        throw new IllegalStateException(errorMessage.toString(), lastException);
    }

    private void selectCategoryPath(
            List<String> categoryPath,
            int attempt
    ) {
        actions.openFilter(FilterSelectors.CATEGORY_FILTER);

        for (int index = 0; index < categoryPath.size(); index++) {
            String category = categoryPath.get(index);

            double timeoutMs =
                    attempt == 1 && index == 0
                            ? FIRST_ROOT_OPTION_TIMEOUT_MS
                            : STANDARD_OPTION_TIMEOUT_MS;

            log.debug(
                    "[FILTER CATEGORY] Waiting for exact option: {} (timeout={}ms)",
                    category,
                    (int) timeoutMs
            );

            try {
                selectExactCategoryWithCompatibilityFallback(
                        category,
                        index,
                        categoryPath.size(),
                        attempt
                );

            } catch (RuntimeException exception) {
                throw new CategorySelectionException(
                        category,
                        timeoutMs,
                        exception
                );
            }
        }
    }

    private void selectExactCategoryWithCompatibilityFallback(
            String category,
            int categoryIndex,
            int categoryPathSize,
            int attempt
    ) {
        try {
            /*
             * A quoted role-selector name is an exact accessible-name match.
             * Keep this as the preferred path so similarly named categories
             * are never selected just because one name contains another.
             */
            actions.clickSelector(exactCategoryRoleSelector(category));

            log.info(
                    "[FILTER CATEGORY] Selected exact option: {}",
                    category
            );
            return;

        } catch (RuntimeException exactException) {
            boolean finalLeaf = categoryIndex == categoryPathSize - 1;
            boolean exactTimedOut = exactException instanceof TimeoutError;

            /*
             * Vinted has started rendering some final category rows with an
             * accessible name that contains extra UI text even though the
             * visible category label itself is unchanged. The strict selector
             * introduced for category safety then cannot see the leaf at all.
             *
             * Only after all three exact attempts, and only for the final
             * category leaf, fall back to the pre-existing role-name locator.
             * Playwright strict mode still rejects ambiguous matches instead
             * of silently choosing one. Parent/root levels stay exact-only.
             * Model selection is completely separate and remains fail-closed
             * against S25 / S25 FE / Edge style variants.
             */
            if (!finalLeaf || attempt < MAX_ATTEMPTS || !exactTimedOut) {
                throw exactException;
            }

            log.warn(
                    "[FILTER CATEGORY] Exact accessible-name lookup for final leaf '{}' timed out on attempt {}/{}. Trying the legacy role-name compatibility locator once; ambiguous matches will still fail closed.",
                    category,
                    attempt,
                    MAX_ATTEMPTS
            );

            actions.selectOption(category);

            log.info(
                    "[FILTER CATEGORY] Selected final leaf '{}' through compatibility locator after exact lookup timed out.",
                    category
            );
        }
    }

    static String exactCategoryRoleSelector(String category) {
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("Category cannot be blank");
        }

        String escaped = category
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");

        return "role=button[name=\"" + escaped + "\"]";
    }

    private void logSelectionFailure(
            int attempt,
            String category,
            Throwable exception,
            double timeoutMilliseconds
    ) {
        String message;

        if (exception instanceof TimeoutError) {
            message = "exact option not visible after "
                    + Math.round(timeoutMilliseconds / 1_000)
                    + "s";
        } else {
            message = getFriendlyErrorMessage(exception);
        }

        if (attempt < MAX_ATTEMPTS) {
            log.info(
                    "[FILTER CATEGORY] Attempt {}/{} needs retry at exact option '{}': {}.",
                    attempt,
                    MAX_ATTEMPTS,
                    category,
                    message
            );
        } else {
            log.warn(
                    "[FILTER CATEGORY] Final attempt {}/{} failed at exact option '{}': {}.",
                    attempt,
                    MAX_ATTEMPTS,
                    category,
                    message
            );
        }

        log.trace(
                "[FILTER CATEGORY] Full category selection error. Attempt {}/{}, category '{}'.",
                attempt,
                MAX_ATTEMPTS,
                category,
                exception
        );
    }

    private void resetBeforeRetry(int failedAttempt) {
        log.info(
                "[FILTER CATEGORY] Resetting page after failed attempt {}/{}.",
                failedAttempt,
                MAX_ATTEMPTS
        );

        try {
            actions.reloadCurrentPage();
            log.info("[FILTER CATEGORY] Page reset completed.");
        } catch (RuntimeException reloadException) {
            log.warn(
                    "[FILTER CATEGORY] Page reset failed: {}",
                    getFriendlyErrorMessage(reloadException)
            );
            log.trace(
                    "[FILTER CATEGORY] Full page reset error.",
                    reloadException
            );
        }

        log.info(
                "[FILTER CATEGORY] Next attempt in {}ms.",
                (int) RETRY_DELAY_MS
        );
        actions.waitForTimeout(RETRY_DELAY_MS);
    }

    private String formatPath(List<String> categoryPath) {
        return String.join(" > ", categoryPath);
    }

    private String getFriendlyErrorMessage(Throwable exception) {
        if (exception == null) {
            return "Unknown error";
        }

        if (exception instanceof CategorySelectionException
                && exception.getCause() != null) {
            return getFriendlyErrorMessage(exception.getCause());
        }

        if (exception instanceof TimeoutError) {
            return "Vinted did not render the exact expected option within the timeout";
        }

        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }

        int firstLineEnd = message.indexOf('\n');
        if (firstLineEnd > 0) {
            return message.substring(0, firstLineEnd).trim();
        }

        return message.trim();
    }

    private static class CategorySelectionException extends RuntimeException {

        private final String category;
        private final double timeoutMilliseconds;

        private CategorySelectionException(
                String category,
                double timeoutMilliseconds,
                Throwable cause
        ) {
            super("Could not select category: " + category, cause);
            this.category = category;
            this.timeoutMilliseconds = timeoutMilliseconds;
        }

        private String getCategory() {
            return category;
        }

        private double getTimeoutMilliseconds() {
            return timeoutMilliseconds;
        }
    }
}
