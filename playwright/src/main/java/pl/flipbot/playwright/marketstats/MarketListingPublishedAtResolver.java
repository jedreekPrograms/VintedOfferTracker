package pl.flipbot.playwright.marketstats;

import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.scanner.model.Listing;

import java.util.List;

/**
 * Publication-time enrichment is intentionally disabled on this stable-derived
 * branch. Market statistics keep the 2026-09-06 first-seen/baseline semantics;
 * this hook exists only so the observer can reuse the proven exact Vinted
 * model-filter scan path without pulling in the later publication-time stack.
 */
final class MarketListingPublishedAtResolver {

    MarketListingPublishedAtResolver(BotContext context) {
        // Intentionally no-op.
    }

    void captureIfNeeded(List<Listing> listings) {
        // Intentionally no-op.
    }
}
