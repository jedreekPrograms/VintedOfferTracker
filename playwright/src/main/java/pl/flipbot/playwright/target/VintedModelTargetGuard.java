package pl.flipbot.playwright.target;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Final, conservative consistency guard for VINTED_MODEL listings.
 *
 * The Vinted model filter remains the primary classifier. This guard does not
 * try to reclassify every seller-written title. It only rejects a candidate
 * when the already-open item page provides conclusive evidence of a different
 * model (for example configured Galaxy S25 vs visible Galaxy Z Flip 4), or an
 * explicitly different variant (for example S25 vs S25 Ultra).
 *
 * Ambiguous/generic titles are deliberately accepted here because absence of
 * model evidence is not proof that Vinted's exact catalog classification was
 * wrong.
 */
public final class VintedModelTargetGuard {

    private static final Pattern COMPACT_MODEL_TOKEN =
            Pattern.compile("^([a-z]+)(\\d+[a-z]*)$");

    private static final Set<String> VARIANT_TOKENS = Set.of(
            "plus",
            "ultra",
            "pro",
            "max",
            "fe",
            "lite",
            "mini",
            "edge"
    );

    private static final Set<String> TECHNICAL_CODE_PREFIXES =
            Set.of("sm");

    /*
     * Only known model-family words may be joined with a following number.
     * Without this bound, generic seller text such as "telefon 128 GB" would
     * incorrectly create a fake model key "telefon128" and become a false
     * mismatch. Compact identities such as S25, A55, X7 or P60 are handled by
     * COMPACT_MODEL_TOKEN and do not need to appear here.
     */
    private static final Set<String> SPLIT_MODEL_FAMILIES = Set.of(
            "flip",
            "fold",
            "pixel",
            "iphone",
            "note",
            "mate",
            "oneplus",
            "xiaomi"
    );

    public Optional<String> findConclusiveMismatch(
            String configuredModel,
            String visibleItemTitle
    ) {
        List<String> targetTokens = tokenize(configuredModel);
        List<String> observedTokens = tokenize(visibleItemTitle);

        Set<String> targetKeys = extractModelKeys(targetTokens);
        Set<String> observedKeys = extractModelKeys(observedTokens);

        if (targetKeys.isEmpty() || observedKeys.isEmpty()) {
            return Optional.empty();
        }

        Set<String> sharedKeys = new HashSet<>(targetKeys);
        sharedKeys.retainAll(observedKeys);

        if (sharedKeys.isEmpty()) {
            return Optional.of(
                    "configured model keys " + targetKeys
                            + " conflict with visible item-title model keys "
                            + observedKeys
                            + " (title='" + normalizeVisibleText(visibleItemTitle) + "')"
            );
        }

        Set<String> targetVariants = extractVariants(targetTokens);
        Set<String> observedVariants = extractVariants(observedTokens);
        observedVariants.removeAll(targetVariants);

        if (!observedVariants.isEmpty()) {
            return Optional.of(
                    "visible item title contains unexpected model variant(s) "
                            + observedVariants
                            + " for configured model '"
                            + normalizeVisibleText(configuredModel)
                            + "' (title='"
                            + normalizeVisibleText(visibleItemTitle)
                            + "')"
            );
        }

        return Optional.empty();
    }

    private Set<String> extractModelKeys(List<String> tokens) {
        Set<String> result = new LinkedHashSet<>();

        for (int index = 0; index < tokens.size(); index++) {
            String token = tokens.get(index);

            Matcher compact = COMPACT_MODEL_TOKEN.matcher(token);
            if (compact.matches()) {
                if (!isTechnicalCode(tokens, index)) {
                    result.add(token);
                }
                continue;
            }

            if (index + 1 >= tokens.size()) {
                continue;
            }

            String next = tokens.get(index + 1);
            if (!SPLIT_MODEL_FAMILIES.contains(token)
                    || !next.matches("^\\d+[a-z]*$")) {
                continue;
            }

            result.add(token + next);
        }

        return result;
    }

    private boolean isTechnicalCode(List<String> tokens, int tokenIndex) {
        if (tokenIndex <= 0) {
            return false;
        }
        return TECHNICAL_CODE_PREFIXES.contains(tokens.get(tokenIndex - 1));
    }

    private Set<String> extractVariants(List<String> tokens) {
        Set<String> variants = new LinkedHashSet<>();
        for (String token : tokens) {
            if (VARIANT_TOKENS.contains(token)) {
                variants.add(token);
            }
        }
        return variants;
    }

    private List<String> tokenize(String value) {
        String normalized = normalizeForMatching(value);
        if (normalized.isBlank()) {
            return List.of();
        }

        List<String> result = new ArrayList<>();
        for (String token : normalized.split("\\s+")) {
            if (!token.isBlank()) {
                result.add(token);
            }
        }
        return result;
    }

    private String normalizeForMatching(String value) {
        if (value == null) {
            return "";
        }

        String withPlus = value.replace("+", " plus ");
        String decomposed = Normalizer.normalize(
                withPlus,
                Normalizer.Form.NFD
        );

        return decomposed
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private String normalizeVisibleText(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }
}
