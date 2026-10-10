package pl.flipbot.playwright.marketstats;

/**
 * Read-only extraction of per-listing publication timestamps from the
 * already-loaded catalog hydration. No additional network requests.
 */
final class MarketCatalogPublicationDomScript {
    private MarketCatalogPublicationDomScript() { }

    static final String EXTRACT_CATALOG_TIMESTAMPS_SCRIPT = """
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

}
