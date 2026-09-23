package pl.flipbot.playwright.negotiation;

/**
 * Shared live item-detail navigation budget for one catalog work run.
 *
 * <p>The same instance is passed through every product in a multi-product
 * catalog scan so adding products cannot multiply expensive item-page
 * inspections. Cache hits and exact current Vinted-model proof do not consume
 * this budget because they perform no live item-page navigation.</p>
 */
public final class CatalogDetailInspectionBudget {

    private final int limit;
    private int used;

    public CatalogDetailInspectionBudget(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException(
                    "Catalog detail inspection limit must be positive"
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
