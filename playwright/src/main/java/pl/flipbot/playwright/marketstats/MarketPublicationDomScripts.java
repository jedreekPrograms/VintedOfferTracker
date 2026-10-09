package pl.flipbot.playwright.marketstats;

/**
 * Read-only DOM probes for publication evidence and explicit Vinted traffic blocks.
 * The order of the published-date fallbacks and their exact scripts are preserved.
 * Do not replace JSON-LD / hydration evidence with a guessed data-testid.
 */
final class MarketPublicationDomScripts {
    private MarketPublicationDomScripts() { }

    static final String EXTRACT_PUBLISHED_AT_SCRIPT = """
            (listingId) => {
                const normalize = (value) =>
                    String(value ?? "")
                        .replace(/\\u00a0/g, " ")
                        .replace(/\\s+/g, " ")
                        .trim();

                const fromExactUploadDateField = () => {
                    const field = document.querySelector(
                        '[data-testid="item-attributes-upload_date"]'
                    );
                    if (!field) {
                        return null;
                    }

                    const value = field.querySelector(
                        '[itemprop="upload_date"]'
                    );
                    if (!value) {
                        return null;
                    }

                    const datetimeElement = value.matches('[datetime]')
                        ? value
                        : value.querySelector('[datetime]');
                    if (datetimeElement) {
                        const datetime = normalize(
                            datetimeElement.getAttribute('datetime')
                        );
                        if (datetime) {
                            return `ISO|${datetime}`;
                        }
                    }

                    const text = normalize(value.textContent);
                    return text ? `REL|${text}` : null;
                };

                const looksRelative = (value) => {
                    const lower = normalize(value).toLowerCase();
                    if (!lower) {
                        return false;
                    }

                    if (lower === "teraz"
                            || lower === "wczoraj"
                            || lower === "przedwczoraj"
                            || lower === "dzisiaj"
                            || lower.startsWith("przed chwil")) {
                        return true;
                    }

                    return /^\\d/.test(lower)
                        && ["sek", "min", "godz", " h", "dzie", "dni", " d", "tyg", "mies", "rok", "lat"]
                            .some(unit => lower.includes(unit));
                };

                const fromAddedLabel = () => {
                    if (!document.body) {
                        return null;
                    }

                    const walker = document.createTreeWalker(
                        document.body,
                        NodeFilter.SHOW_TEXT
                    );

                    const textNodes = [];
                    let node;

                    while ((node = walker.nextNode())) {
                        const text = normalize(node.nodeValue);
                        if (text) {
                            textNodes.push({ node, text });
                        }
                    }

                    for (let index = 0; index < textNodes.length; index++) {
                        const item = textNodes[index];
                        if (item.text.toLowerCase() !== "dodane") {
                            continue;
                        }

                        let container = item.node.parentElement;

                        for (let depth = 0; container && depth < 6; depth++) {
                            const time = container.querySelector("time[datetime]");
                            if (time) {
                                const datetime = normalize(
                                    time.getAttribute("datetime")
                                );
                                if (datetime) {
                                    return `ISO|${datetime}`;
                                }
                            }

                            const datetimeElement =
                                container.querySelector("[datetime]");
                            if (datetimeElement) {
                                const datetime = normalize(
                                    datetimeElement.getAttribute("datetime")
                                );
                                if (datetime) {
                                    return `ISO|${datetime}`;
                                }
                            }

                            const containerText = normalize(
                                container.textContent
                            );
                            if (containerText.toLowerCase().startsWith("dodane ")) {
                                const candidate = normalize(
                                    containerText.substring("dodane".length)
                                );
                                if (looksRelative(candidate)) {
                                    return `REL|${candidate}`;
                                }
                            }

                            container = container.parentElement;
                        }

                        for (
                            let nextIndex = index + 1;
                            nextIndex < Math.min(textNodes.length, index + 12);
                            nextIndex++
                        ) {
                            const candidate = textNodes[nextIndex].text;
                            if (looksRelative(candidate)) {
                                return `REL|${candidate}`;
                            }
                        }
                    }

                    return null;
                };

                const jsonLdDate = () => {
                    const dateKeys = [
                        "datePublished",
                        "uploadDate",
                        "dateCreated"
                    ];

                    const visit = (value) => {
                        if (Array.isArray(value)) {
                            for (const child of value) {
                                const found = visit(child);
                                if (found) {
                                    return found;
                                }
                            }
                            return null;
                        }

                        if (!value || typeof value !== "object") {
                            return null;
                        }

                        const rawType = value["@type"];
                        const types = Array.isArray(rawType)
                            ? rawType
                            : [rawType];

                        const isItem = types.some(type => {
                            const normalizedType =
                                normalize(type).toLowerCase();
                            return normalizedType === "product"
                                || normalizedType === "offer";
                        });

                        if (isItem) {
                            for (const key of dateKeys) {
                                const candidate = value[key];
                                if (typeof candidate === "string"
                                        && normalize(candidate)) {
                                    return normalize(candidate);
                                }
                            }
                        }

                        for (const child of Object.values(value)) {
                            const found = visit(child);
                            if (found) {
                                return found;
                            }
                        }

                        return null;
                    };

                    for (const script of document.querySelectorAll(
                        'script[type="application/ld+json"]'
                    )) {
                        try {
                            const found = visit(
                                JSON.parse(script.textContent || "null")
                            );
                            if (found) {
                                return found;
                            }
                        } catch (_) {
                            // Ignore malformed third-party JSON-LD.
                        }
                    }

                    return null;
                };

                const hydratedCreatedAt = () => {
                    const id = normalize(listingId);
                    if (!id || !document.documentElement) {
                        return null;
                    }

                    const slash = String.fromCharCode(92);
                    const raw = document.documentElement.outerHTML || "";
                    const decoded = raw
                        .split(`${slash}u0022`).join('"')
                        .split(`${slash}\"`).join('"');
                    const variants = decoded === raw
                        ? [raw]
                        : [raw, decoded];

                    const idNeedles = [
                        `"id":${id}`,
                        `"id":"${id}"`,
                        `"item_id":${id}`,
                        `"item_id":"${id}"`
                    ];
                    const timestampKey = '"created_at_ts"';

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

                            if (end >= source.length) {
                                return null;
                            }

                            return normalize(source.slice(cursor, end));
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

                    for (const source of variants) {
                        for (const needle of idNeedles) {
                            let cursor = 0;

                            while (cursor < source.length) {
                                const idIndex =
                                    source.indexOf(needle, cursor);
                                if (idIndex < 0) {
                                    break;
                                }

                                const from = Math.max(0, idIndex - 20000);
                                const to = Math.min(
                                    source.length,
                                    idIndex + 20000
                                );
                                const fragment = source.slice(from, to);
                                const localIdIndex = idIndex - from;

                                let timestampIndex =
                                    fragment.indexOf(timestampKey);
                                let bestValue = null;
                                let bestDistance =
                                    Number.POSITIVE_INFINITY;

                                while (timestampIndex >= 0) {
                                    const value = readValue(
                                        fragment,
                                        timestampIndex
                                    );

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
                                        timestampIndex
                                            + timestampKey.length
                                    );
                                }

                                if (bestValue !== null) {
                                    return bestValue;
                                }

                                cursor = idIndex + needle.length;
                            }
                        }
                    }

                    return null;
                };

                const exactUploadDate = fromExactUploadDateField();
                if (exactUploadDate) {
                    return exactUploadDate;
                }

                const visibleAdded = fromAddedLabel();
                if (visibleAdded) {
                    return visibleAdded;
                }

                const absolute = jsonLdDate();
                if (absolute) {
                    return `ISO|${absolute}`;
                }

                const hydrated = hydratedCreatedAt();
                return hydrated
                    ? `ISO|${hydrated}`
                    : null;
            }
            """;

    static final String BLOCK_PAGE_SCRIPT = """
            () => {
                const text = String(
                    document.body?.innerText
                        || document.documentElement?.innerText
                        || ""
                ).toLowerCase();

                return text.includes(
                    "access to this site is blocked for this computer"
                ) || text.includes(
                    "twoja sesja została zablokowana"
                );
            }
            """;

}
