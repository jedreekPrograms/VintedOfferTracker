package pl.flipbot.playwright.scanner;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ListingSelectors {

    public static final String ITEM =
            "[data-testid^='product-item-id-']";

    public static final String TITLE =
            "[data-testid$='--description-title']";

    public static final String CONDITION =
            "[data-testid$='--description-subtitle']";

    public static final String PRICE =
            "[data-testid$='--price-text']";

    public static final String IMAGE =
            "[data-testid$='--image--img']";

    public static final String LINK =
            "[data-testid$='--overlay-link']";

    public static final String FAVORITES =
            "[data-testid='favourite-count-text']";

}