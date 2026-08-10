package pl.flipbot.playwright.context;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.browser.BrowserManager;
import pl.flipbot.playwright.model.BotDetailsDto;
import pl.flipbot.playwright.session.SessionManager;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Getter
public class BotContext implements AutoCloseable {

    private final BotDetailsDto bot;

    private final BrowserContext browserContext;

    private final Page page;

    private final SessionManager sessionManager;


    public BotContext(
            BotDetailsDto bot,
            BrowserManager browserManager
    ) {

        this.bot =
                bot;


        this.sessionManager =
                new SessionManager();


        Path sessionFile =
                null;


        if (
                sessionManager.sessionExists(
                        bot.getId()
                )
        ) {

            sessionFile =
                    sessionManager.sessionFile(
                            bot.getId()
                    );
        }


        this.browserContext =
                browserManager.createContext(
                        sessionFile
                );


        /*
         * Wybieramy główną stronę bota.
         *
         * Jeżeli w kontekście została jakaś
         * strona Vinted, używamy jej.
         *
         * W przeciwnym razie tworzymy nową.
         */
        this.page =
                resolveMainPage();


        /*
         * Zamykamy stare dodatkowe karty,
         * które mogły zostać zapisane / otwarte
         * wcześniej.
         */
        closeExistingExtraPages();


        /*
         * Od tej chwili każda nowa karta
         * lub popup zostanie automatycznie
         * zamknięty.
         */
        registerPopupGuard();
    }


    private Page resolveMainPage() {

        List<Page> existingPages =
                browserContext.pages();


        /*
         * Najpierw szukamy istniejącej
         * strony Vinted.
         */
        for (
                Page existingPage
                : existingPages
        ) {

            if (
                    isVintedPage(
                            existingPage
                    )
            ) {

                log.info(
                        "[BROWSER] Reusing existing Vinted page: {}",
                        existingPage.url()
                );


                return existingPage;
            }
        }


        /*
         * Jeżeli istnieje jakaś zwykła karta,
         * możemy wykorzystać pierwszą.
         *
         * LoginService i tak później wykona
         * navigate() na Vinted.
         */
        if (
                !existingPages.isEmpty()
        ) {

            Page existingPage =
                    existingPages.get(0);


            log.info(
                    "[BROWSER] Reusing existing browser page: {}",
                    existingPage.url()
            );


            return existingPage;
        }


        log.info(
                "[BROWSER] No existing page found. Creating new page."
        );


        return browserContext.newPage();
    }


    private void closeExistingExtraPages() {

        /*
         * Robimy kopię listy, ponieważ podczas
         * zamykania stron browserContext.pages()
         * będzie się zmieniać.
         */
        List<Page> pages =
                new ArrayList<>(
                        browserContext.pages()
                );


        for (
                Page existingPage
                : pages
        ) {

            if (
                    existingPage == page
            ) {

                continue;
            }


            closeUnexpectedPage(
                    existingPage,
                    "existing extra page"
            );
        }
    }


    private void registerPopupGuard() {

        browserContext.onPage(
                newPage -> {

                    /*
                     * Głównej strony nigdy
                     * nie zamykamy.
                     */
                    if (
                            newPage == page
                    ) {

                        return;
                    }


                    closeUnexpectedPage(
                            newPage,
                            "new popup/page"
                    );
                }
        );


        log.info(
                "[BROWSER] Popup guard enabled for bot {}.",
                bot.getId()
        );
    }


    private void closeUnexpectedPage(
            Page unexpectedPage,
            String reason
    ) {

        try {

            String url =
                    unexpectedPage.url();


            log.warn(
                    "[BROWSER] Closing unexpected browser page. "
                            + "Bot: {}, reason: {}, URL: {}",
                    bot.getId(),
                    reason,
                    url
            );


            unexpectedPage.close();

        } catch (Exception exception) {

            log.warn(
                    "[BROWSER] Could not close unexpected page for bot {}.",
                    bot.getId(),
                    exception
            );
        }
    }


    private boolean isVintedPage(
            Page candidate
    ) {

        try {

            String url =
                    candidate.url();


            if (
                    url == null
                            || url.isBlank()
            ) {

                return false;
            }


            return url.startsWith(
                    "https://www.vinted.pl"
            )
                    || url.startsWith(
                    "https://vinted.pl"
            );

        } catch (Exception exception) {

            return false;
        }
    }


    public void saveSession() {

        sessionManager.saveSession(
                bot.getId(),
                browserContext
        );
    }


    @Override
    public void close() {

        browserContext.close();
    }
}