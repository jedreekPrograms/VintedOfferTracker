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

    private static final String EXTRACT_CATALOG_TIMESTAMPS_SCRIPT = """
            (listingIds) => {
                const ids = Array.isArray(listingIds)
                    ? listingIds.map(value => String(value ?? "").trim()).filter(Boolean)
                    : [];
                if (ids.length === 0) {
                    return {};
                }

                const normalize = (value) => String(value ?? "").trim();
                const slash = String.fromCharCode(92);
                const raw = document.documentElement?.innerHTML ?? "";
                const decoded = raw
                    .split(`${slash}u0022`).join('"')
                    .split(`${slash}"`).join('"');
                const variants = decoded === raw ? [raw] : [raw, decoded];
                const timestampKey = '"created_at_ts"';
                const result = {};

                const readValue = (source, keyIndex) => {
                    const colon = source.indexOf(
                        ":",
                        keyIndex + timestampKey.length
                    );
                    if (colon < 0) {
                        return null;
                    }

                    let cursor = colon + 1;
                    while (cursor < source.length
                            && source.charCodeAt(cursor) <= 32) {
                        cursor++;
                    }

                    if (cursor >= source.length) {
                        return null;
                    }

                    const quote = source[cursor];
                    if (quote === '"' || quote === "'") {
                        cursor++;
                        let end = cursor;
                        while (end < source.length) {
                            if (source[end] === quote
                                    && source[end - 1] !== slash) {
                                break;
                            }
                            end++;
                        }
                        return end < source.length
                            ? normalize(source.slice(cursor, end))
                            : null;
                    }

                    let end = cursor;
                    while (end < source.length) {
                        const character = source[end];
                        if (character === ","
                                || character === "}"
                                || character === "]"
                                || source.charCodeAt(end) <= 32) {
                            break;
                        }
                        end++;
                    }

                    return normalize(source.slice(cursor, end));
                };

                for (const id of ids) {
                    const idNeedles = [
                        `"id":${id}`,
                        `"id":"${id}"`,
                        `"item_id":${id}`,
                        `"item_id":"${id}"`
                    ];

                    let bestValue = null;
                    let bestDistance = Number.POSITIVE_INFINITY;

                    for (const source of variants) {
                        for (const needle of idNeedles) {
                            let cursor = 0;
                            while (cursor < source.length) {
                                const idIndex = source.indexOf(needle, cursor);
                                if (idIndex < 0) {
                                    break;
                                }

                                const from = Math.max(0, idIndex - 12000);
                                const to = Math.min(source.length, idIndex + 12000);
                                const fragment = source.slice(from, to);
                                const localIdIndex = idIndex - from;

                                let timestampIndex = fragment.indexOf(timestampKey);
                                while (timestampIndex >= 0) {
                                    const value = readValue(fragment, timestampIndex);
                                    if (value) {
                                        const distance = Math.abs(
                                            timestampIndex - localIdIndex
                                        );
                                        if (distance < bestDistance) {
                                            bestDistance = distance;
                                            bestValue = value;
                                        }
                                    }
                                    timestampIndex = fragment.indexOf(
                                        timestampKey,
                                        timestampIndex + timestampKey.length
                                    );
                                }

                                cursor = idIndex + needle.length;
                            }
                        }
                    }

                    if (bestValue) {
                        result[id] = bestValue;
                    }
                }

                return result;
            }
            """;

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
                    EXTRACT_CATALOG_TIMESTAMPS_SCRIPT,
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
        return EXTRACT_CATALOG_TIMESTAMPS_SCRIPT;
    }
}
