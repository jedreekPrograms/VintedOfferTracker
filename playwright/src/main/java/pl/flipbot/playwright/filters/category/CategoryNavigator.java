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
            4;

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

            /*
             * Każda próba musi zaczynać się od czystego stanu.
             *
             * Wcześniej po timeoutcie popup potrafił zostać otwarty
             * na drugim poziomie kategorii. Następne openFilter()
             * mogło wtedy zamknąć / przełączyć popup zamiast
             * otworzyć kategorię od początku.
             */
            actions.dismissOpenOverlaySafely();


            try {

                log.info(
                        "[FILTER] Selecting category path. Attempt {}/{}. Path: {}",
                        attempt,
                        MAX_ATTEMPTS,
                        categoryPath
                );


                selectCategoryPath(
                        categoryPath
                );


                /*
                 * Po wybraniu ostatniej kategorii zamykamy ewentualny
                 * pozostały overlay. Sam wybór kategorii jest już zapisany
                 * przez Vinted w URL.
                 */
                actions.dismissOpenOverlaySafely();


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


                /*
                 * Najważniejsza część retry:
                 * zamykamy niedokończony popup zanim spróbujemy od nowa.
                 */
                actions.dismissOpenOverlaySafely();


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