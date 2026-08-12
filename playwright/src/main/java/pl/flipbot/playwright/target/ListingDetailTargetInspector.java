package pl.flipbot.playwright.target;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.WaitUntilState;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.model.BotConfigurationDto;
import pl.flipbot.playwright.verification.HumanVerificationHandler;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Slf4j
public class ListingDetailTargetInspector {

    private static final double NAVIGATION_TIMEOUT_MS =
            30_000;

    private static final double TITLE_TIMEOUT_MS =
            10_000;


    /*
     * Nie chcemy otwierać tego samego ogłoszenia co 30 sekund.
     *
     * Cache przechowuje wyłącznie pełny tytuł <h1>.
     * Sam wynik matchowania nie jest cache'owany, dzięki czemu
     * zmiana konfiguracji bota nadal przeliczy matcher od nowa.
     */
    private static final long FULL_TITLE_CACHE_TTL_MS =
            30L * 60L * 1_000L;

    private static final int MAX_FULL_TITLE_CACHE_ENTRIES =
            5_000;


    private static final String ITEM_TITLE_SELECTOR =
            "[data-testid='item-page-summary-plugin'] h1";


    private static final String[] RATE_LIMIT_MARKERS = {
            "you are rate limited",
            "too many requests",
            "access to this site is blocked for this computer"
    };


    private final BotContext context;

    private final ListingTargetMatcher
            listingTargetMatcher;

    private final HumanVerificationHandler
            humanVerificationHandler =
            new HumanVerificationHandler();


    private final Map<String, CachedTitle>
            fullTitleCache =
            new LinkedHashMap<>(
                    128,
                    0.75f,
                    true
            ) {

                @Override
                protected boolean removeEldestEntry(
                        Map.Entry<String, CachedTitle> eldest
                ) {

                    return size()
                            > MAX_FULL_TITLE_CACHE_ENTRIES;
                }
            };


    public ListingDetailTargetInspector(
            BotContext context,
            ListingTargetMatcher listingTargetMatcher
    ) {

        this.context =
                context;

        this.listingTargetMatcher =
                listingTargetMatcher;
    }


    public boolean hasCachedFullTitle(
            String marketplaceListingId
    ) {

        return getCachedFullTitle(
                marketplaceListingId
        )
                != null;
    }


    public boolean matchesConfiguredTarget(
            ListingResponseDto listing,
            BotConfigurationDto configuration
    ) {

        if (
                listing == null
                        || listing.url() == null
                        || listing.url().isBlank()
        ) {

            log.warn(
                    "[TARGET DETAIL] Listing has no usable URL. "
                            + "It cannot be inspected safely."
            );

            return false;
        }


        String cachedFullTitle =
                getCachedFullTitle(
                        listing.listingId()
                );


        if (cachedFullTitle != null) {

            boolean cachedMatches =
                    listingTargetMatcher
                            .matchesFullTitle(
                                    cachedFullTitle,
                                    configuration
                            );


            log.info(
                    "[TARGET CACHE] Marketplace listing {} reused cached "
                            + "full title='{}'. Match={}. No Vinted item-page "
                            + "request was performed.",
                    listing.listingId(),
                    cachedFullTitle,
                    cachedMatches
            );


            return cachedMatches;
        }


        Page page =
                context.getPage();


        log.info(
                "[TARGET DETAIL] URL/catalog data is still not decisive "
                        + "for marketplace listing {}. Opening listing page: {}",
                listing.listingId(),
                listing.url()
        );


        page.navigate(
                listing.url(),
                new Page.NavigateOptions()
                        .setWaitUntil(
                                WaitUntilState.DOMCONTENTLOADED
                        )
                        .setTimeout(
                                NAVIGATION_TIMEOUT_MS
                        )
        );


        /*
         * Najpierw sprawdzamy jawny komunikat blokady.
         *
         * Nie próbujemy "przebijać się" przez rate limit.
         * Gdy Vinted go pokaże, rzucamy specjalny wyjątek,
         * a BotWorker robi długi cooldown.
         */
        throwIfRateLimited(
                page,
                listing
        );


        humanVerificationHandler.waitUntilVerified(
                page
        );


        throwIfRateLimited(
                page,
                listing
        );


        Locator itemTitle =
                page.locator(
                                ITEM_TITLE_SELECTOR
                        )
                        .first();


        try {

            itemTitle.waitFor(
                    new Locator.WaitForOptions()
                            .setState(
                                    WaitForSelectorState.VISIBLE
                            )
                            .setTimeout(
                                    TITLE_TIMEOUT_MS
                            )
            );

        } catch (TimeoutError timeoutError) {

            /*
             * Jeżeli Vinted w międzyczasie przełączyło stronę
             * na komunikat rate-limit, rozpoznajemy go tutaj
             * zamiast traktować jako zwykły brak <h1>.
             */
            throwIfRateLimited(
                    page,
                    listing
            );


            throw timeoutError;
        }


        String fullTitle =
                normalizeVisibleText(
                        itemTitle.innerText()
                );


        if (fullTitle.isBlank()) {

            log.warn(
                    "[TARGET DETAIL] Item page title is empty for marketplace "
                            + "listing {}. The listing will be skipped "
                            + "fail-closed for this cycle.",
                    listing.listingId()
            );

            return false;
        }


        cacheFullTitle(
                listing.listingId(),
                fullTitle
        );


        boolean matches =
                listingTargetMatcher.matchesFullTitle(
                        fullTitle,
                        configuration
                );


        if (matches) {

            log.info(
                    "[TARGET DETAIL] Marketplace listing {} MATCHES after "
                            + "opening the item page. Catalog title='{}', "
                            + "full item title='{}'.",
                    listing.listingId(),
                    listing.title(),
                    fullTitle
            );

        } else {

            log.info(
                    "[TARGET DETAIL] Marketplace listing {} does NOT match "
                            + "after opening the item page. Catalog title='{}', "
                            + "full item title='{}'.",
                    listing.listingId(),
                    listing.title(),
                    fullTitle
            );
        }


        return matches;
    }


    private void throwIfRateLimited(
            Page page,
            ListingResponseDto listing
    ) {

        String pageText =
                readPageTextSafely(
                        page
                );


        if (pageText.isBlank()) {

            return;
        }


        for (String marker : RATE_LIMIT_MARKERS) {

            if (
                    pageText.contains(
                            marker
                    )
            ) {

                throw new VintedRateLimitException(
                        "Vinted rate limit detected while inspecting "
                                + "marketplace listing "
                                + listing.listingId()
                                + ". Marker: '"
                                + marker
                                + "'."
                );
            }
        }
    }


    private String readPageTextSafely(
            Page page
    ) {

        try {

            String title =
                    page.title();

            String body =
                    page.locator(
                                    "body"
                            )
                            .innerText();


            return (
                    (title == null ? "" : title)
                            + " "
                            + (body == null ? "" : body)
            )
                    .toLowerCase(
                            Locale.ROOT
                    );

        } catch (PlaywrightException exception) {

            log.debug(
                    "[TARGET DETAIL] Page changed while checking "
                            + "for rate-limit markers.",
                    exception
            );


            return "";
        }
    }


    private void cacheFullTitle(
            String marketplaceListingId,
            String fullTitle
    ) {

        if (
                marketplaceListingId == null
                        || marketplaceListingId.isBlank()
                        || fullTitle == null
                        || fullTitle.isBlank()
        ) {

            return;
        }


        fullTitleCache.put(
                marketplaceListingId,
                new CachedTitle(
                        fullTitle,
                        System.currentTimeMillis()
                )
        );
    }


    private String getCachedFullTitle(
            String marketplaceListingId
    ) {

        if (
                marketplaceListingId == null
                        || marketplaceListingId.isBlank()
        ) {

            return null;
        }


        CachedTitle cachedTitle =
                fullTitleCache.get(
                        marketplaceListingId
                );


        if (cachedTitle == null) {

            return null;
        }


        long age =
                System.currentTimeMillis()
                        - cachedTitle.cachedAtMillis();


        if (
                age > FULL_TITLE_CACHE_TTL_MS
        ) {

            fullTitleCache.remove(
                    marketplaceListingId
            );


            return null;
        }


        return cachedTitle.title();
    }


    private String normalizeVisibleText(
            String value
    ) {

        if (value == null) {

            return "";
        }


        return value
                .trim()
                .replaceAll(
                        "\\s+",
                        " "
                );
    }


    private record CachedTitle(
            String title,
            long cachedAtMillis
    ) {
    }
}