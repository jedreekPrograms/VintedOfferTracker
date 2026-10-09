package pl.flipbot.playwright.filters;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Pure, testable model-option identity rules. The browser and retry logic stays
 * in FilterActions: this class must never infer a model from a partial match.
 */
final class VintedModelOptionIdentity {

    static final String MODEL_TEST_ID_PREFIX =
            "selectable-item-brand_collection-";
    static final String MODEL_TITLE_TEST_ID_SUFFIX = "--title";

    private VintedModelOptionIdentity() {
    }

    static String canonicalModelRowTestId(String collectionId) {
        if (collectionId == null
                || !collectionId.matches("^\\d+$")) {
            throw new IllegalArgumentException(
                    "Model collection id must contain digits only"
            );
        }

        return MODEL_TEST_ID_PREFIX + collectionId;
    }

    static String modelCollectionIdFromTestId(String testId) {
        if (testId == null || !testId.startsWith(MODEL_TEST_ID_PREFIX)) {
            return null;
        }

        String candidate = testId.substring(MODEL_TEST_ID_PREFIX.length()).trim();

        if (candidate.endsWith(MODEL_TITLE_TEST_ID_SUFFIX)) {
            candidate = candidate.substring(
                    0,
                    candidate.length() - MODEL_TITLE_TEST_ID_SUFFIX.length()
            );
        }

        if (!candidate.matches("^\\d+$")) {
            return null;
        }

        return candidate;
    }

    static Pattern exactModelOptionPattern(String model) {
        String normalizedModel = normalizeOptionText(model);
        return Pattern.compile(
                "^\\s*" + Pattern.quote(normalizedModel) + "\\s*$",
                Pattern.CASE_INSENSITIVE
        );
    }

    static boolean exactVisibleModelLabelMatches(
            String requestedModel,
            String visibleText
    ) {
        String normalizedRequested = normalizeOptionText(requestedModel);

        if (normalizedRequested.isBlank() || visibleText == null) {
            return false;
        }

        String normalizedVisible = normalizeOptionText(visibleText);
        if (normalizedRequested.equalsIgnoreCase(normalizedVisible)) {
            return true;
        }

        if (startsWithIgnoreCase(normalizedVisible, normalizedRequested)) {
            String suffix = normalizedVisible
                    .substring(normalizedRequested.length())
                    .trim();
            if (isModelOptionMetadata(suffix)) {
                return true;
            }
        }

        List<String> lines = visibleText.lines()
                .map(VintedModelOptionIdentity::normalizeOptionText)
                .filter(line -> !line.isBlank())
                .toList();

        for (int index = 0; index < lines.size(); index++) {
            if (!normalizedRequested.equalsIgnoreCase(lines.get(index))) {
                continue;
            }

            boolean onlyMetadataAroundExactLabel = true;
            for (int otherIndex = 0; otherIndex < lines.size(); otherIndex++) {
                if (otherIndex == index) {
                    continue;
                }

                if (!isModelOptionMetadata(lines.get(otherIndex))) {
                    onlyMetadataAroundExactLabel = false;
                    break;
                }
            }

            if (onlyMetadataAroundExactLabel) {
                return true;
            }
        }

        return false;
    }

    private static boolean startsWithIgnoreCase(String value, String prefix) {
        return value.length() >= prefix.length()
                && value.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    private static boolean isModelOptionMetadata(String value) {
        String normalized = normalizeOptionText(value);
        if (normalized.isBlank()) {
            return false;
        }

        if (normalized.matches("^\\d[\\d\\s.,]*$")) {
            return true;
        }

        String lower = normalized.toLowerCase(Locale.ROOT);
        return lower.matches(
                "^\\d[\\d\\s.,]*\\s*(przedmiot\\p{L}*|item\\p{L}*|article\\p{L}*|result\\p{L}*|wynik\\p{L}*)$"
        );
    }

    static String normalizeOptionText(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }

}
