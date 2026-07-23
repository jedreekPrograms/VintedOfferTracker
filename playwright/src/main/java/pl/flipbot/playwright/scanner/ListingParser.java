package pl.flipbot.playwright.scanner;

import com.microsoft.playwright.Locator;
import pl.flipbot.playwright.scanner.model.Listing;

import java.math.BigDecimal;

public class ListingParser {

    public Listing parse(Locator item) {

        Listing listing = new Listing();

        listing.setId(
                extractId(item)
        );

        listing.setTitle(
                text(item, ListingSelectors.TITLE)
        );

        listing.setCondition(
                text(item, ListingSelectors.CONDITION)
        );

        listing.setPrice(
                price(
                        text(item, ListingSelectors.PRICE)
                )
        );

        listing.setUrl(
                attribute(
                        item,
                        ListingSelectors.LINK,
                        "href"
                )
        );

        listing.setImageUrl(
                attribute(
                        item,
                        ListingSelectors.IMAGE,
                        "src"
                )
        );

        listing.setFavoriteCount(
                integer(
                        text(item, ListingSelectors.FAVORITES)
                )
        );

        return listing;

    }

    private String extractId(Locator item) {

        String testId = item.getAttribute("data-testid");

        if (testId == null) {
            return null;
        }

        if (testId.startsWith("product-item-id-")) {
            return testId.substring("product-item-id-".length());
        }

        if (testId.startsWith("item-")) {
            return testId.substring("item-".length());
        }

        return testId;

    }

    private String text(Locator item, String selector) {

        Locator locator = item.locator(selector);

        if (locator.count() == 0) {
            return null;
        }

        return locator.textContent();

    }

    private String attribute(Locator item, String selector, String attribute) {

        Locator locator = item.locator(selector);

        if (locator.count() == 0) {
            return null;
        }

        return locator.getAttribute(attribute);

    }

    private Integer integer(String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        return Integer.parseInt(
                value.trim()
        );

    }

    private BigDecimal price(String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value
                .replace("zł", "")
                .replace("\u00A0", "")
                .replace(" ", "")
                .replace(",", ".");

        return new BigDecimal(normalized);

    }

}