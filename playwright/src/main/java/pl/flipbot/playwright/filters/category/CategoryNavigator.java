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

    private static final int MAX_ATTEMPTS =
            3;

    private static final double RETRY_DELAY_MS =
            1_000;


    private final FilterActions actions;


    public void select(
            List<String> categoryPath
    ) {

        if (
                categoryPath == null
                        || categoryPath.isEmpty()
        ) {

            return;
        }


        RuntimeException lastException =
                null;


        for (
                int attempt = 1;
                attempt <= MAX_ATTEMPTS;
                attempt++
        ) {

            try {

                log.info(
                        "[FILTER] Selecting category path. Attempt {}/{}. Path: {}",
                        attempt,
                        MAX_ATTEMPTS,
                        categoryPath
                );


                /*
                 * To jest dokładnie styl nawigacji, na którym
                 * filtr kategorii działał wcześniej:
                 *
                 * otwórz filtr
                 * -> poczekaj na Elektronika
                 * -> kliknij Elektronika
                 * -> poczekaj na następny poziom
                 * -> kliknij ...
                 *
                 * Nie wciskamy Escape pomiędzy próbami i nie
                 * zmieniamy semantyki accessible-name.
                 */
                selectCategoryPath(
                        categoryPath
                );


                log.info(
                        "[FILTER] Category path selected successfully: {}",
                        categoryPath
                );


                return;

            } catch (RuntimeException exception) {

                lastException =
                        exception;


                log.warn(
                        "[FILTER] Category selection attempt {}/{} failed: {}",
                        attempt,
                        MAX_ATTEMPTS,
                        getFriendlyErrorMessage(
                                exception
                        )
                );


                log.debug(
                        "[FILTER] Full category selection error. Attempt {}/{}.",
                        attempt,
                        MAX_ATTEMPTS,
                        exception
                );


                if (
                        attempt < MAX_ATTEMPTS
                ) {

                    log.info(
                            "[FILTER] Retrying category selection in {} ms.",
                            (int) RETRY_DELAY_MS
                    );


                    actions.waitForTimeout(
                            RETRY_DELAY_MS
                    );
                }
            }
        }


        String finalErrorMessage =
                lastException != null
                        ? getFriendlyErrorMessage(
                        lastException
                )
                        : "Unknown category selection error";


        throw new IllegalStateException(
                "Could not select category path after "
                        + MAX_ATTEMPTS
                        + " attempts. Path: "
                        + categoryPath
                        + ". Last error: "
                        + finalErrorMessage,
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

            log.info(
                    "[FILTER] Waiting for category option: {}",
                    category
            );


            actions.waitForOption(
                    category
            );


            actions.selectOption(
                    category
            );
        }
    }


    private String getFriendlyErrorMessage(
            RuntimeException exception
    ) {

        if (
                exception instanceof TimeoutError
        ) {

            return "Vinted did not render the expected category option within the timeout";
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