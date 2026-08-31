package pl.flipbot.playwright.target;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.WaitUntilState;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.model.BotConfigurationDto;
import pl.flipbot.playwright.verification.HumanVerificationHandler;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Slf4j
public class ListingDetailTargetInspector {

    private static final double NAVIGATION_TIMEOUT_MS = 30_000;
    private static final double IDENTITY_TIMEOUT_MS = 10_000;
    private static final double IDENTITY_POLL_INTERVAL_MS = 250;

    private static final long IDENTITY_CACHE_TTL_MS =
            30L * 60L * 1_000L;

    private static final int MAX_IDENTITY_CACHE_ENTRIES = 5_000;

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
    private final VintedModelTargetGuard vintedModelTargetGuard =
            new VintedModelTargetGuard();
    private final VintedItemIdentityReader itemIdentityReader =
            new VintedItemIdentityReader();
    private final HumanVerificationHandler humanVerificationHandler =
            new HumanVerificationHandler();

    private final Map<String, CachedIdentity> identityCache =
            new LinkedHashMap<>(128, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(
                        Map.Entry<String, CachedIdentity> eldest
                ) {
                    return size() > MAX_IDENTITY_CACHE_ENTRIES;
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
        CachedIdentity cached = getCachedIdentity(marketplaceListingId);
        return cached != null
                && cached.identity() != null
                && !cached.identity().title().isBlank();
    }

    public boolean matchesConfiguredTarget(
            ListingResponseDto listing,
            BotConfigurationDto configuration
    ) {
        if (listing == null
                || listing.url() == null
                || listing.url().isBlank()) {
            log.warn(
                    "[TARGET DETAIL] Listing has no usable URL. It cannot be verified safely."
            );
            return false;
        }

        if (listingTargetMatcher.usesVintedModelFilter(configuration)) {
            return matchesVintedModelTarget(listing, configuration);
        }

        return matchesSearchQueryTarget(listing, configuration);
    }

    private boolean matchesVintedModelTarget(
            ListingResponseDto listing,
            BotConfigurationDto configuration
    ) {
        VintedItemIdentityReader.ItemIdentity identity =
                getOrLoadIdentity(listing);

        String configuredBrand = normalizeIdentity(configuration.getBrand());
        String observedBrand = normalizeIdentity(identity.brand());

        if (!configuredBrand.isBlank()
                && !observedBrand.isBlank()
                && !configuredBrand.equals(observedBrand)) {
            log.error(
                    "[TARGET DETAIL] Marketplace listing {} is wrong brand for bot target. Configured brand='{}', structured Vinted brand='{}'.",
                    listing.listingId(),
                    configuration.getBrand(),
                    identity.brand()
            );
            return false;
        }

        if (identity.hasStructuredModel()) {
            Optional<String> mismatch =
                    vintedModelTargetGuard.findConclusiveMismatch(
                            configuration.getModel(),
                            identity.model()
                    );

            if (mismatch.isPresent()) {
                log.error(
                        "[TARGET DETAIL] Marketplace listing {} failed structured Vinted model verification. Configured model='{}', structured model='{}'. Reason: {}",
                        listing.listingId(),
                        configuration.getModel(),
                        identity.model(),
                        mismatch.get()
                );
                return false;
            }

            if (vintedModelTargetGuard.provesConfiguredModel(
                    configuration.getModel(),
                    identity.model()
            )) {
                log.info(
                        "[TARGET DETAIL] Marketplace listing {} positively verified from structured Vinted identity. Brand='{}', model='{}', h1='{}'.",
                        listing.listingId(),
                        identity.brand(),
                        identity.model(),
                        identity.title()
                );
                return true;
            }

            throw new IllegalStateException(
                    "Structured Vinted model field could not positively prove configured model '"
                            + configuration.getModel()
                            + "' for marketplace listing "
                            + listing.listingId()
                            + ". Structured model='"
                            + identity.model()
                            + "'. Failing closed for this cycle."
            );
        }

        ListingTargetAssessment titleAssessment =
                listingTargetMatcher.assessVisibleText(
                        identity.title(),
                        configuration
                );

        if (titleAssessment == ListingTargetAssessment.MATCH) {
            log.info(
                    "[TARGET DETAIL] Marketplace listing {} has no readable structured model field, but live h1 positively proves configured Vinted model '{}'. h1='{}'.",
                    listing.listingId(),
                    configuration.getModel(),
                    identity.title()
            );
            return true;
        }

        if (titleAssessment == ListingTargetAssessment.MISMATCH) {
            log.error(
                    "[TARGET DETAIL] Marketplace listing {} has no readable structured model field and live h1 conclusively conflicts with configured Vinted model '{}'. h1='{}'.",
                    listing.listingId(),
                    configuration.getModel(),
                    identity.title()
            );
            return false;
        }

        /*
         * Generic titles such as "Tablet z wyświetlaczem do wymiany" must not
         * be treated as proof of Galaxy S25. If Vinted's structured Model field
         * cannot be read, keep the listing DISCOVERED and retry later instead
         * of either sending an unsafe offer or permanently misclassifying it.
         */
        throw new IllegalStateException(
                "Live Vinted item identity is ambiguous for configured model '"
                        + configuration.getModel()
                        + "' and marketplace listing "
                        + listing.listingId()
                        + ". h1='"
                        + identity.title()
                        + "', structured brand='"
                        + identity.brand()
                        + "', structured model is unavailable. Failing closed for this cycle."
        );
    }

    private boolean matchesSearchQueryTarget(
            ListingResponseDto listing,
            BotConfigurationDto configuration
    ) {
        VintedItemIdentityReader.ItemIdentity identity =
                getOrLoadIdentity(listing);

        String fullTitle = identity.title();
        if (fullTitle.isBlank()) {
            throw new IllegalStateException(
                    "No visible item h1 was found for marketplace listing "
                            + listing.listingId()
            );
        }

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

    private VintedItemIdentityReader.ItemIdentity getOrLoadIdentity(
            ListingResponseDto listing
    ) {
        CachedIdentity cached = getCachedIdentity(listing.listingId());
        if (cached != null) {
            log.info(
                    "[TARGET CACHE] Marketplace listing {} reused cached live identity. h1='{}', brand='{}', model='{}'. No Vinted item-page request was performed.",
                    listing.listingId(),
                    cached.identity().title(),
                    cached.identity().brand(),
                    cached.identity().model()
            );
            return cached.identity();
        }

        Page page = context.getPage();

        log.info(
                "[TARGET DETAIL] Opening marketplace listing {} for live identity verification: {}",
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

        VintedItemIdentityReader.ItemIdentity identity =
                waitForReadableIdentity(page, listing);

        cacheIdentity(listing.listingId(), identity);
        return identity;
    }

    private VintedItemIdentityReader.ItemIdentity waitForReadableIdentity(
            Page page,
            ListingResponseDto listing
    ) {
        long deadline = System.currentTimeMillis()
                + (long) IDENTITY_TIMEOUT_MS;

        VintedItemIdentityReader.ItemIdentity lastIdentity =
                VintedItemIdentityReader.ItemIdentity.empty();

        while (System.currentTimeMillis() < deadline) {
            throwIfRateLimited(page, listing);
            throwIfListingUnavailable(page, listing);

            lastIdentity = itemIdentityReader.read(page);

            if (lastIdentity.hasStructuredModel()
                    || !lastIdentity.title().isBlank()) {
                return lastIdentity;
            }

            page.waitForTimeout(IDENTITY_POLL_INTERVAL_MS);
        }

        throwIfRateLimited(page, listing);
        throwIfListingUnavailable(page, listing);

        throw new IllegalStateException(
                "No readable Vinted item identity was found within "
                        + Math.round(IDENTITY_TIMEOUT_MS / 1_000)
                        + " seconds for marketplace listing "
                        + listing.listingId()
                        + ". Current URL: "
                        + page.url()
                        + ". Last h1='"
                        + lastIdentity.title()
                        + "', brand='"
                        + lastIdentity.brand()
                        + "', model='"
                        + lastIdentity.model()
                        + "'."
        );
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

    private void cacheIdentity(
            String marketplaceListingId,
            VintedItemIdentityReader.ItemIdentity identity
    ) {
        if (marketplaceListingId == null
                || marketplaceListingId.isBlank()
                || identity == null) {
            return;
        }

        identityCache.put(
                marketplaceListingId,
                new CachedIdentity(
                        identity,
                        System.currentTimeMillis()
                )
        );
    }

    private CachedIdentity getCachedIdentity(
            String marketplaceListingId
    ) {
        if (marketplaceListingId == null
                || marketplaceListingId.isBlank()) {
            return null;
        }

        CachedIdentity cached = identityCache.get(marketplaceListingId);
        if (cached == null) {
            return null;
        }

        long age = System.currentTimeMillis() - cached.cachedAtMillis();
        if (age > IDENTITY_CACHE_TTL_MS) {
            identityCache.remove(marketplaceListingId);
            return null;
        }

        return cached;
    }

    private String normalizeIdentity(String value) {
        if (value == null) {
            return "";
        }

        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private record CachedIdentity(
            VintedItemIdentityReader.ItemIdentity identity,
            long cachedAtMillis
    ) {
    }
}
