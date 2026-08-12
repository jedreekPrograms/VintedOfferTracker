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

    private static final int OPTION_TIMEOUT_SECONDS = 10;

    private final FilterActions actions;


    public void select(
            List<String> categoryPath
    ) {

        if (
                categoryPath == null
                        || categoryPath.isEmpty()
        ) {

            log.debug(
                    "[FILTER CATEGORY] No category path configured. Skipping."
            );

            return;
        }


        RuntimeException lastException = null;
        String failedCategory = null;


        for (
                int attempt = 1;
                attempt <= MAX_ATTEMPTS;
                attempt++
        ) {

            try {

                log.info(
                        "[FILTER CATEGORY] Attempt {}/{} started. Path: {}",
                        attempt,
                        MAX_ATTEMPTS,
                        formatPath(categoryPath)
                );


                selectCategoryPath(
                        categoryPath
                );


                log.info(
                        "[FILTER CATEGORY] Category selected successfully: {}",
                        formatPath(categoryPath)
                );


                return;

            } catch (CategorySelectionException exception) {

                lastException = exception;
                failedCategory = exception.getCategory();


                logSelectionFailure(
                        attempt,
                        exception.getCategory(),
                        exception.getCause()
                );


                if (
                        attempt < MAX_ATTEMPTS
                ) {

                    resetBeforeRetry(
                            attempt
                    );
                }

            } catch (RuntimeException exception) {

                lastException = exception;


                log.warn(
                        "[FILTER CATEGORY] Attempt {}/{} failed because of unexpected error: {}",
                        attempt,
                        MAX_ATTEMPTS,
                        getFriendlyErrorMessage(exception)
                );


                log.trace(
                        "[FILTER CATEGORY] Full unexpected error for attempt {}/{}.",
                        attempt,
                        MAX_ATTEMPTS,
                        exception
                );


                if (
                        attempt < MAX_ATTEMPTS
                ) {

                    resetBeforeRetry(
                            attempt
                    );
                }
            }
        }


        StringBuilder errorMessage =
                new StringBuilder(
                        "Could not select category path after "
                                + MAX_ATTEMPTS
                                + " attempts: "
                                + formatPath(categoryPath)
                );


        if (
                failedCategory != null
        ) {

            errorMessage.append(
                    ". Last failed category: "
            );

            errorMessage.append(
                    failedCategory
            );
        }


        if (
                lastException != null
        ) {

            errorMessage.append(
                    ". Last error: "
            );

            errorMessage.append(
                    getFriendlyErrorMessage(
                            lastException
                    )
            );
        }


        throw new IllegalStateException(
                errorMessage.toString(),
                lastException
        );
    }


    private void selectCategoryPath(
            List<String> categoryPath
    ) {

        actions.openFilter(
                FilterSelectors.CATEGORY_FILTER
        );


        for (
                String category
                : categoryPath
        ) {

            log.debug(
                    "[FILTER CATEGORY] Waiting for: {}",
                    category
            );


            try {

                actions.waitForOption(
                        category
                );


                actions.selectOption(
                        category
                );


                log.info(
                        "[FILTER CATEGORY] Selected: {}",
                        category
                );

            } catch (RuntimeException exception) {

                throw new CategorySelectionException(
                        category,
                        exception
                );
            }
        }
    }


    private void logSelectionFailure(
            int attempt,
            String category,
            Throwable exception
    ) {

        if (
                exception instanceof TimeoutError
        ) {

            log.warn(
                    "[FILTER CATEGORY] Attempt {}/{} failed at '{}': option not visible after {}s.",
                    attempt,
                    MAX_ATTEMPTS,
                    category,
                    OPTION_TIMEOUT_SECONDS
            );

        } else {

            log.warn(
                    "[FILTER CATEGORY] Attempt {}/{} failed at '{}': {}",
                    attempt,
                    MAX_ATTEMPTS,
                    category,
                    getFriendlyErrorMessage(
                            exception
                    )
            );
        }


        /*
         * Full Playwright stack trace is intentionally TRACE only.
         *
         * A missing Vinted category option is recoverable and expected
         * from time to time, so DEBUG/INFO logs should stay readable.
         */
        log.trace(
                "[FILTER CATEGORY] Full category selection error. "
                        + "Attempt {}/{}, category '{}'.",
                attempt,
                MAX_ATTEMPTS,
                category,
                exception
        );
    }


    private void resetBeforeRetry(
            int failedAttempt
    ) {

        log.info(
                "[FILTER CATEGORY] Resetting page after failed attempt {}/{}.",
                failedAttempt,
                MAX_ATTEMPTS
        );


        try {

            actions.reloadCurrentPage();


            log.info(
                    "[FILTER CATEGORY] Page reset completed."
            );

        } catch (RuntimeException reloadException) {

            log.warn(
                    "[FILTER CATEGORY] Page reset failed: {}",
                    getFriendlyErrorMessage(
                            reloadException
                    )
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


        actions.waitForTimeout(
                RETRY_DELAY_MS
        );
    }


    private String formatPath(
            List<String> categoryPath
    ) {

        return String.join(
                " > ",
                categoryPath
        );
    }


    private String getFriendlyErrorMessage(
            Throwable exception
    ) {

        if (
                exception == null
        ) {

            return "Unknown error";
        }


        if (
                exception instanceof CategorySelectionException
                        && exception.getCause() != null
        ) {

            return getFriendlyErrorMessage(
                    exception.getCause()
            );
        }


        if (
                exception instanceof TimeoutError
        ) {

            return "Vinted did not render the expected option within the timeout";
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


    private static class CategorySelectionException
            extends RuntimeException {

        private final String category;


        private CategorySelectionException(
                String category,
                Throwable cause
        ) {

            super(
                    "Could not select category: "
                            + category,
                    cause
            );


            this.category =
                    category;
        }


        private String getCategory() {

            return category;
        }
    }
}