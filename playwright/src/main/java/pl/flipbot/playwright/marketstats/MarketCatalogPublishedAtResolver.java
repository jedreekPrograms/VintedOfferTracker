package pl.flipbot.playwright.marketstats;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.scanner.model.Listing;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves Vinted publication timestamps from data already present in the
 * filtered catalog page. This path performs no network requests: it only reads
 * the current document hydration and therefore does not add item-detail traffic.
 */
@Slf4j
final class MarketCatalogPublishedAtResolver {

    private static final ZoneId MARKET_ZONE = ZoneId.of("Europe/Warsaw");

    private final BotContext context;
    private final ObjectMapper objectMapper = new ObjectMapper();

    MarketCatalogPublishedAtResolver(BotContext context) {
        this.context = context;
    }

    void captureIfAvailable(List<Listing> listings) {
        if (context == null || listings == null || listings.isEmpty()) {
            return;
        }

        List<String> ids = new ArrayList<>();

        for (Listing listing : listings) {
            if (listing == null || listing.getId() == null || listing.getId().isBlank()) {
                continue;
            }

            String id = listing.getId().trim();
            MarketStatsObservationContext.recordObservedListingId(id);

            if (MarketStatsObservationContext.needsPublicationResolution(id)) {
                ids.add(id);
            }
        }

        if (ids.isEmpty()) {
            return;
        }

        try {
            Object rawResult = context.getPage().evaluate(
                    MarketCatalogPublicationDomScript.EXTRACT_CATALOG_TIMESTAMPS_SCRIPT,
                    ids
            );

            Map<String, String> timestamps = objectMapper.convertValue(
                    rawResult,
                    new TypeReference<Map<String, String>>() {
                    }
            );

            LocalDateTime observedAt = LocalDateTime.now(MARKET_ZONE);
            int resolved = 0;

            for (Map.Entry<String, String> entry : timestamps.entrySet()) {
                var parsed = VintedPublishedAtParser.parse(
                        "ISO|" + entry.getValue(),
                        observedAt
                );

                if (parsed.isPresent()) {
                    MarketStatsObservationContext.recordPublishedAt(
                            entry.getKey(),
                            parsed.get()
                    );
                    resolved++;
                }
            }

            log.info(
                    "[MARKET STATS] Catalog hydration resolved {}/{} required publication timestamps without extra Vinted requests.",
                    resolved,
                    ids.size()
            );
        } catch (RuntimeException exception) {
            log.debug(
                    "[MARKET STATS] Catalog hydration publication lookup was unavailable; unresolved listings may use the paced detail fallback.",
                    exception
            );
        }
    }

    static String extractCatalogTimestampsScript() {
        return MarketCatalogPublicationDomScript.EXTRACT_CATALOG_TIMESTAMPS_SCRIPT;
    }
}
