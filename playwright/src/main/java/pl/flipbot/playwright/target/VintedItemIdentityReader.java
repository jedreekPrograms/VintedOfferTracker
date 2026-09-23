package pl.flipbot.playwright.target;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Reads identity evidence from an already-open Vinted item page.
 *
 * Seller-written h1 text is useful but not authoritative. Vinted also renders
 * structured product attributes such as "Marka" and "Model" inside the item
 * summary. Those fields are the strongest live evidence available before a
 * real offer is submitted and are therefore read separately.
 */
public final class VintedItemIdentityReader {

    private static final String SUMMARY_SELECTOR =
            "[data-testid='item-page-summary-plugin']";

    private static final String[] TITLE_SELECTORS = {
            "[data-testid='item-page-summary-plugin'] h1",
            "main h1",
            "h1"
    };

    private static final Set<String> BRAND_LABELS = Set.of(
            "marka",
            "brand"
    );

    private static final Set<String> MODEL_LABELS = Set.of(
            "model"
    );

    public ItemIdentity read(Page page) {
        if (page == null || page.isClosed()) {
            return ItemIdentity.empty();
        }

        String title = readVisibleTitle(page);
        String summaryText = readSummaryText(page);

        List<String> lines = toMeaningfulLines(summaryText);
        String brand = readLabelledValue(lines, BRAND_LABELS);
        String model = readLabelledValue(lines, MODEL_LABELS);

        return new ItemIdentity(
                title,
                brand,
                model
        );
    }

    private String readVisibleTitle(Page page) {
        for (String selector : TITLE_SELECTORS) {
            try {
                Locator candidates = page.locator(selector);
                int count = Math.min(candidates.count(), 10);

                for (int index = 0; index < count; index++) {
                    Locator candidate = candidates.nth(index);
                    if (!candidate.isVisible()) {
                        continue;
                    }

                    String value = normalizeVisibleText(candidate.innerText());
                    if (!value.isBlank()) {
                        return value;
                    }
                }
            } catch (PlaywrightException ignored) {
                // DOM may be replacing the item summary. Try next selector.
            }
        }

        return "";
    }

    private String readSummaryText(Page page) {
        try {
            Locator summary = page.locator(SUMMARY_SELECTOR).first();
            if (!summary.isVisible()) {
                return "";
            }

            String value = summary.innerText();
            return value == null ? "" : value;
        } catch (PlaywrightException exception) {
            return "";
        }
    }

    static List<String> toMeaningfulLines(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }

        List<String> result = new ArrayList<>();
        for (String rawLine : value.split("\\R")) {
            String line = normalizeVisibleText(rawLine);
            if (!line.isBlank()) {
                result.add(line);
            }
        }
        return result;
    }

    static String readLabelledValue(
            List<String> lines,
            Set<String> normalizedLabels
    ) {
        if (lines == null || lines.isEmpty()) {
            return "";
        }

        for (int index = 0; index < lines.size(); index++) {
            String line = normalizeVisibleText(lines.get(index));
            String normalizedLine = normalizeForLabelMatching(line);

            for (String label : normalizedLabels) {
                if (normalizedLine.equals(label)) {
                    for (int valueIndex = index + 1;
                         valueIndex < lines.size();
                         valueIndex++) {
                        String candidate = normalizeVisibleText(lines.get(valueIndex));
                        if (!candidate.isBlank()) {
                            return candidate;
                        }
                    }
                    return "";
                }

                String colonPrefix = label + ":";
                if (normalizedLine.startsWith(colonPrefix)) {
                    String suffix = line.substring(
                            Math.min(line.length(), line.indexOf(':') + 1)
                    ).trim();
                    if (!suffix.isBlank()) {
                        return suffix;
                    }
                }

                String spacePrefix = label + " ";
                if (normalizedLine.startsWith(spacePrefix)) {
                    int separatorIndex = firstWhitespaceIndex(line);
                    if (separatorIndex >= 0
                            && separatorIndex + 1 < line.length()) {
                        String suffix = line.substring(separatorIndex + 1).trim();
                        if (!suffix.isBlank()) {
                            return suffix;
                        }
                    }
                }
            }
        }

        return "";
    }

    private static int firstWhitespaceIndex(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isWhitespace(value.charAt(index))) {
                return index;
            }
        }
        return -1;
    }

    private static String normalizeForLabelMatching(String value) {
        if (value == null) {
            return "";
        }

        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String normalizeVisibleText(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    public record ItemIdentity(
            String title,
            String brand,
            String model
    ) {
        public ItemIdentity {
            title = title == null ? "" : title;
            brand = brand == null ? "" : brand;
            model = model == null ? "" : model;
        }

        public static ItemIdentity empty() {
            return new ItemIdentity("", "", "");
        }

        public boolean hasStructuredModel() {
            return !model.isBlank();
        }

        public boolean hasStructuredBrand() {
            return !brand.isBlank();
        }
    }
}
