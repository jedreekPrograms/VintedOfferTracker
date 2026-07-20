package pl.flipbot.playwright.filters;

public final class FilterSelectors {

    private FilterSelectors() {
    }

    // Filter buttons

    public static final String CATEGORY_FILTER =
            "catalog--catalog-filter--trigger";

    public static final String BRAND_FILTER =
            "catalog--brand-filter--trigger";

    public static final String MODEL_FILTER =
            "catalog--brand_collection-filter--trigger";

    public static final String PRICE_FILTER =
            "catalog--price-filter--trigger";

    // Price inputs

    public static final String PRICE_FROM =
            "price_from";

    public static final String PRICE_TO =
            "price_to";

}