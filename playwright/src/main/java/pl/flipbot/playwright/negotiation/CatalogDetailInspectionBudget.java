package pl.flipbot.playwright.negotiation;

/**
 * Run-scoped budget for real Vinted item-detail page requests performed while
 * processing one catalog scan. Cached identity checks and exact current-model
 * provenance do not consume this budget because they do not navigate to an
 * item page.
 */
public final class CatalogDetailInspectionBudget {

    private final int limit;
    private int used;

    public CatalogDetailInspectionBudget(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException(
                    "Catalog detail inspection limit must be positive."
            );
        }
        this.limit = limit;
    }

    public boolean tryAcquire() {
        if (used >= limit) {
            return false;
        }

        used++;
        return true;
    }

    public int limit() {
        return limit;
    }

    public int used() {
        return used;
    }

    public int remaining() {
        return Math.max(0, limit - used);
    }

    public boolean exhausted() {
        return used >= limit;
    }
}
