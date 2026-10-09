package pl.flipbot.playwright.negotiation;

import java.math.BigDecimal;

/**
 * Parsing rules for prices observed on Vinted.
 *
 * parse() remains the strict parser used by the minimum-offer feedback. The
 * two confirmation parsers retain their distinct pre-refactor acceptance
 * policies: first offer accepts any numeric value; next step requires > 0.
 */
final class VintedPriceParser {

    private VintedPriceParser() {
    }

    static BigDecimal parse(String rawPrice) {
        String normalized = normalize(rawPrice);

        if (normalized.isBlank()) {
            throw new IllegalArgumentException(
                    "Price text contains no numeric value: " + rawPrice
            );
        }

        BigDecimal price = new BigDecimal(normalized);
        if (price.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Price must be greater than zero: " + rawPrice
            );
        }
        return price;
    }

    static BigDecimal parseFirstOfferConfirmation(String rawPrice) {
        return new BigDecimal(normalize(rawPrice));
    }

    static BigDecimal parseNextStepConfirmation(String rawPrice) {
        String normalized = normalize(rawPrice);

        if (normalized.isBlank()) {
            throw new IllegalArgumentException(
                    "Price contains no numeric value: " + rawPrice
            );
        }

        BigDecimal price = new BigDecimal(normalized);
        if (price.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Price must be greater than zero: " + rawPrice
            );
        }
        return price;
    }

    private static String normalize(String rawPrice) {
        if (rawPrice == null || rawPrice.isBlank()) {
            throw new IllegalArgumentException("Price text cannot be blank");
        }

        String normalized = rawPrice
                .replace("\u00A0", "")
                .replace("\u202F", "")
                .replace(" ", "")
                .replaceAll("[^0-9,.-]", "");

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

        return normalized;
    }
}
