package pl.flipbot.playwright.target;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
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

    private static final double NAVIGATION_TIMEOUT_MS = 30_000;
    private static final double TITLE_TIMEOUT_MS = 10_000;
    private static final double TITLE_POLL_INTERVAL_MS = 250;

    private static final long FULL_TITLE_CACHE_TTL_MS =
            30L * 60L * 1_000L;

    private static final int MAX_FULL_TITLE_CACHE_ENTRIES = 5_000;

    /*
     * The first selector is Vinted's normal item-page structure. The generic
     * fallbacks matter for alternate/cross-border item layouts where the same
     * visible title is rendered outside item-page-summary-plugin.
     */
    private static final String[] ITEM_TITLE_SELECTORS = {
            "[data-testid='item-page-summary-plugin'] h1",
            "main h1",
            "h1"
    };

    private static final String[] RATE_LIMIT_MARKERS = {
            "you are rate limited",
            "too many requests",
            "access to this site is blocked for this computer"
    };

    private static final String[] UNAVAILABLE_MARKERS = {
            "page not found",
            "check the link is correct",
            "nie znaleziono strony",
            "sprawdź, czy link jest poprawny",
            "item is no longer available",
            "ogłoszenie nie jest już dostępne"
    };

    private final BotContext context;
    private final ListingTargetMatcher listingTargetMatcher;
    private final HumanVerificationHandler humanVerificationHandler =
            new HumanVerificationHandler();

    private final Map<String, CachedTitle> fullTitleCache =
            new LinkedHashMap<>(128, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(
                        Map.Entry<String, CachedTitle> eldest
                ) {
                    return size() > MAX_FULL_TITLE_CACHE_ENTRIES;
                }
            };

    public ListingDetailTargetInspector(
            BotContext context,
            ListingTargetMatcher listingTargetMatcher
    ) {
        this.context = context;
        this.listingTargetMatcher = listingTargetMatcher;
    }

    public boolean hasCachedFullTitle(
            String marketplaceListingId
    ) {
        return getCachedFullTitle(marketplaceListingId) != null;
    }

    public boolean matchesConfiguredTarget(
            ListingResponseDto listing,
            BotConfigurationDto configuration
    ) {
        /*
         * VINTED_MODEL has already been proven by FilterActions and persisted
         * in the catalog URL. Opening every item page to reinterpret the title
         * would be both wasteful and conceptually wrong: seller-written text
         * is not the target classifier in this mode.
         */
        if (listingTargetMatcher.usesVintedModelFilter(configuration)) {
            log.debug(
                    "[TARGET DETAIL] Marketplace listing {} accepted without semantic item-title verification because VINTED_MODEL trusts the exact persisted Vinted model filter.",
                    listing == null ? null : listing.listingId()
            );
            return true;
        }

        if (listing == null
                || listing.url() == null
                || listing.url().isBlank()) {
            log.warn(
                    "[TARGET DETAIL] Listing has no usable URL. It cannot be inspected safely for SEARCH_QUERY."
            );
            return false;
        }

        String cachedFullTitle = getCachedFullTitle(
                listing.listingId()
        );

        if (cachedFullTitle != null) {
            boolean cachedMatches = listingTargetMatcher.matchesFullTitle(
                    cachedFullTitle,
                    configuration
            );

            log.info(
                    "[TARGET CACHE] Marketplace listing {} reused cached full title='{}'. Match={}. No Vinted item-page request was performed.",
                    listing.listingId(),
                    cachedFullTitle,
                    cachedMatches
            );

            return cachedMatches;
        }

        Page page = context.getPage();

        log.info(
                "[TARGET DETAIL] Opening marketplace listing {} for live SEARCH_QUERY title verification: {}",
                listing.listingId(),
                listing.url()
        );

        page.navigate(
                listing.url(),
                new Page.NavigateOptions()
                        .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout(NAVIGATION_TIMEOUT_MS)
        );

        throwIfRateLimited(page, listing);
        humanVerificationHandler.waitUntilVerified(page);
        throwIfRateLimited(page, listing);

        Locator itemTitle = waitForVisibleItemTitle(
                page,
                listing
        );

        String fullTitle = normalizeVisibleText(
                itemTitle.innerText()
        );

        if (fullTitle.isBlank()) {
            log.warn(
                    "[TARGET DETAIL] Item page title is empty for marketplace listing {}. The listing will be skipped fail-closed for this cycle.",
                    listing.listingId()
            );
            return false;
        }

        cacheFullTitle(
                listing.listingId(),
                fullTitle
        );

        boolean matches = listingTargetMatcher.matchesFullTitle(
                fullTitle,
                configuration
        );

        if (matches) {
            log.info(
                    "[TARGET DETAIL] Marketplace listing {} MATCHES SEARCH_QUERY after live item-page verification. Catalog title='{}', full item title='{}'.",
                    listing.listingId(),
                    listing.title(),
                    fullTitle
            );
        } else {
            log.info(
                    "[TARGET DETAIL] Marketplace listing {} does NOT match SEARCH_QUERY after live item-page verification. Catalog title='{}', full item title='{}'.",
                    listing.listingId(),
                    listing.title(),
                    fullTitle
            );
        }

        return matches;
    }

    private Locator waitForVisibleItemTitle(
            Page page,
            ListingResponseDto listing
    ) {
        long deadline = System.currentTimeMillis()
                + (long) TITLE_TIMEOUT_MS;

        while (System.currentTimeMillis() < deadline) {
            throwIfRateLimited(page, listing);
            throwIfListingUnavailable(page, listing);

            Locator visibleTitle = findVisibleItemTitle(page);
            if (visibleTitle != null) {
                log.info(
                        "[TARGET DETAIL] Visible item title found for marketplace listing {} using live page URL {}.",
                        listing.listingId(),
                        page.url()
                );
                return visibleTitle;
            }

            page.waitForTimeout(TITLE_POLL_INTERVAL_MS);
        }

        throwIfRateLimited(page, listing);
        throwIfListingUnavailable(page, listing);

        throw new IllegalStateException(
                "No visible item h1 was found within "
                        + Math.round(TITLE_TIMEOUT_MS / 1_000)
                        + " seconds for marketplace listing "
                        + listing.listingId()
                        + ". Current URL: "
                        + page.url()
                        + ". Checked selectors: "
                        + String.join(", ", ITEM_TITLE_SELECTORS)
        );
    }

    private Locator findVisibleItemTitle(Page page) {
        for (String selector : ITEM_TITLE_SELECTORS) {
            Locator candidates = page.locator(selector);
            int count;

            try {
                count = Math.min(candidates.count(), 10);
            } catch (PlaywrightException exception) {
                continue;
            }

            for (int index = 0; index < count; index++) {
                Locator candidate = candidates.nth(index);

                try {
                    if (!candidate.isVisible()) {
                        continue;
                    }

                    String text = normalizeVisibleText(candidate.innerText());
                    if (!text.isBlank()) {
                        return candidate;
                    }
                } catch (PlaywrightException ignored) {
                    // DOM changed while polling. Try the next candidate/poll.
                }
            }
        }

        return null;
    }

    private void throwIfRateLimited(
            Page page,
            ListingResponseDto listing
    ) {
        String pageText = readPageTextSafely(page);

        if (pageText.isBlank()) {
            return;
        }

        for (String marker : RATE_LIMIT_MARKERS) {
            if (pageText.contains(marker)) {
                throw new VintedRateLimitException(
                        "Vinted rate limit detected while inspecting marketplace listing "
                                + listing.listingId()
                                + ". Marker: '"
                                + marker
                                + "'."
                );
            }
        }
    }

    private void throwIfListingUnavailable(
            Page page,
            ListingResponseDto listing
    ) {
        String pageText = readPageTextSafely(page);
        if (pageText.isBlank()) {
            return;
        }

        for (String marker : UNAVAILABLE_MARKERS) {
            if (pageText.contains(marker)) {
                throw new ListingUnavailableDuringVerificationException(
                        "Marketplace listing "
                                + listing.listingId()
                                + " is unavailable during live verification. Marker: '"
                                + marker
                                + "'. Current URL: "
                                + page.url()
                );
            }
        }
    }

    private String readPageTextSafely(Page page) {
        try {
            String title = page.title();
            String body = page.locator("body").innerText();

            return ((title == null ? "" : title)
                    + " "
                    + (body == null ? "" : body))
                    .toLowerCase(Locale.ROOT);
        } catch (PlaywrightException exception) {
            log.debug(
                    "[TARGET DETAIL] Page changed while checking page markers.",
                    exception
            );
            return "";
        }
    }

    private void cacheFullTitle(
            String marketplaceListingId,
            String fullTitle
    ) {
        if (marketplaceListingId == null
                || marketplaceListingId.isBlank()
                || fullTitle == null
                || fullTitle.isBlank()) {
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
        if (marketplaceListingId == null
                || marketplaceListingId.isBlank()) {
            return null;
        }

        CachedTitle cachedTitle = fullTitleCache.get(
                marketplaceListingId
        );

        if (cachedTitle == null) {
            return null;
        }

        long age = System.currentTimeMillis()
                - cachedTitle.cachedAtMillis();

        if (age > FULL_TITLE_CACHE_TTL_MS) {
            fullTitleCache.remove(marketplaceListingId);
            return null;
        }

        return cachedTitle.title();
    }

    private String normalizeVisibleText(String value) {
        if (value == null) {
            return "";
        }

        return value
                .trim()
                .replaceAll("\\s+", " ");
    }

    private record CachedTitle(
            String title,
            long cachedAtMillis
    ) {
    }
}
