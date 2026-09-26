package pl.flipbot.playwright.negotiation;

import java.math.BigDecimal;

final class VintedPriceParser {

    private VintedPriceParser() {
    }

    static BigDecimal parse(String rawPrice) {
        if (rawPrice == null || rawPrice.isBlank()) {
            throw new IllegalArgumentException(
                    "Price text cannot be blank"
            );
        }

        String normalized = rawPrice
                .replace("\u00A0", "")
                .replace("\u202F", "")
                .replace(" ", "")
                .replaceAll("[^0-9,.-]", "");

        if (normalized.isBlank()) {
            throw new IllegalArgumentException(
                    "Price text contains no numeric value: " + rawPrice
            );
        }

        if (normalized.contains(",") && normalized.contains(".")) {
            int lastComma = normalized.lastIndexOf(',');
            int lastDot = normalized.lastIndexOf('.');

            if (lastComma > lastDot) {
                normalized = normalized
                        .replace(".", "")
                        .replace(',', '.');
            } else {
                normalized = normalized.replace(",", "");
            }
        } else if (normalized.contains(",")) {
            normalized = normalized.replace(',', '.');
        }

        BigDecimal price = new BigDecimal(normalized);

        if (price.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Price must be greater than zero: " + rawPrice
            );
        }

        return price;
    }
}
