package pl.flipbot.playwright.scanner;

import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.scanner.model.Listing;
import pl.flipbot.playwright.scanner.model.ListingSnapshot;

import java.math.BigDecimal;

@Slf4j
public class ListingParser {

    private static final String PRODUCT_ITEM_PREFIX =
            "product-item-id-";

    private static final String ITEM_PREFIX =
            "item-";

    public Listing parse(ListingSnapshot snapshot) {

        Listing listing = new Listing();

        listing.setId(
                extractId(snapshot.testId())
        );

        listing.setTitle(
                emptyToNull(snapshot.title())
        );

        listing.setCondition(
                emptyToNull(snapshot.condition())
        );

        listing.setPrice(
                price(snapshot.price())
        );

        listing.setUrl(
                emptyToNull(snapshot.url())
        );

        listing.setImageUrl(
                emptyToNull(snapshot.imageUrl())
        );

        listing.setFavoriteCount(
                integer(snapshot.favoriteCount())
        );

        return listing;

    }

    private String extractId(String testId) {

        if (testId == null || testId.isBlank()) {
            return null;
        }

        if (testId.startsWith(PRODUCT_ITEM_PREFIX)) {

            return testId.substring(
                    PRODUCT_ITEM_PREFIX.length()
            );

        }

        if (testId.startsWith(ITEM_PREFIX)) {

            return testId.substring(
                    ITEM_PREFIX.length()
            );

        }

        return testId;

    }

    private BigDecimal price(String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value
                .replace("\u00A0", "")
                .replace(" ", "")
                .replaceAll("[^0-9,.-]", "");

        if (normalized.isBlank()) {
            return null;
        }

        normalized = normalizeNumber(normalized);

        try {

            return new BigDecimal(normalized);

        } catch (NumberFormatException exception) {

            log.warn(
                    "Could not parse listing price: {}",
                    value
            );

            return null;

        }

    }

    private String normalizeNumber(String value) {

        int commaIndex = value.lastIndexOf(',');
        int dotIndex = value.lastIndexOf('.');

        if (commaIndex >= 0 && dotIndex >= 0) {

            if (commaIndex > dotIndex) {

                return value
                        .replace(".", "")
                        .replace(',', '.');

            }

            return value.replace(",", "");

        }

        if (commaIndex >= 0) {

            int decimalPlaces =
                    value.length() - commaIndex - 1;

            if (decimalPlaces == 2) {

                return value.replace(',', '.');

            }

            return value.replace(",", "");

        }

        if (dotIndex >= 0) {

            int decimalPlaces =
                    value.length() - dotIndex - 1;

            if (decimalPlaces != 2) {

                return value.replace(".", "");

            }

        }

        return value;

    }

    private Integer integer(String value) {

        if (value == null || value.isBlank()) {
            return 0;
        }

        String digits = value.replaceAll("[^0-9]", "");

        if (digits.isBlank()) {
            return 0;
        }

        try {

            return Integer.valueOf(digits);

        } catch (NumberFormatException exception) {

            log.warn(
                    "Could not parse favorite count: {}",
                    value
            );

            return 0;

        }

    }

    private String emptyToNull(String value) {

        if (value == null) {
            return null;
        }

        String trimmed = value.trim();

        return trimmed.isEmpty()
                ? null
                : trimmed;

    }

}