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
 * Conservative model-identity guard used around VINTED_MODEL targets.
 *
 * The native Vinted filter is useful evidence, but persisted DISCOVERED rows
 * can outlive the catalog scan (or even a later bot configuration change).
 * Therefore model identity must never be inferred solely from the fact that a
 * listing currently belongs to a bot's backlog.
 *
 * This class supports two complementary questions:
 * - can the observed text conclusively prove a DIFFERENT model?
 * - can the observed text positively prove the CONFIGURED model?
 *
 * Generic seller text is neither a match nor a mismatch. Callers must then use
 * stronger evidence, preferably the structured Model field from the item page.
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
            "edge",
            "air"
    );

    private static final Set<String> TECHNICAL_CODE_PREFIXES =
            Set.of("sm");

    /*
     * Vinted's Samsung model labels can sometimes identify the product family
     * without containing a generation key we can safely parse. For example:
     * "Galaxy Tab Active 3" and "Galaxy Tab S" are conclusively tablet-family
     * entries, even though neither exposes an S-series phone key such as s24/s25.
     *
     * Keep this list intentionally narrow. Generic seller wording such as
     * "tablet i ladowarka" is still ambiguous and must use stronger live-item
     * evidence; only the explicit Vinted model-family token "tab" is treated as
     * conclusive family evidence here.
     */
    private static final Set<String> EXPLICIT_PRODUCT_FAMILY_TOKENS =
            Set.of("tab");

    /*
     * Only known model-family words may be joined with a following number.
     * Without this bound, generic seller text such as "telefon 128 GB" would
     * incorrectly create a fake model key "telefon128".
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

    /**
     * Returns a reason only when observed text contains conclusive evidence of
     * a different model/variant. Ambiguous text returns Optional.empty().
     */
    public Optional<String> findConclusiveMismatch(
            String configuredModel,
            String observedText
    ) {
        List<String> targetTokens = tokenize(configuredModel);
        List<String> observedTokens = tokenize(observedText);

        Optional<String> familyMismatch = findExplicitFamilyMismatch(
                targetTokens,
                observedTokens,
                configuredModel,
                observedText
        );
        if (familyMismatch.isPresent()) {
            return familyMismatch;
        }

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
                            + " conflict with observed model keys "
                            + observedKeys
                            + " (observed='" + normalizeVisibleText(observedText) + "')"
            );
        }

        Set<String> targetVariants = extractVariants(targetTokens);
        Set<String> observedVariants = extractVariants(observedTokens);

        Set<String> unexpectedObservedVariants =
                new LinkedHashSet<>(observedVariants);
        unexpectedObservedVariants.removeAll(targetVariants);

        if (!unexpectedObservedVariants.isEmpty()) {
            return Optional.of(
                    "observed text contains unexpected model variant(s) "
                            + unexpectedObservedVariants
                            + " for configured model '"
                            + normalizeVisibleText(configuredModel)
                            + "' (observed='"
                            + normalizeVisibleText(observedText)
                            + "')"
            );
        }

        return Optional.empty();
    }

    /**
     * Positive proof is deliberately stronger than "not a mismatch".
     *
     * Example:
     * - target Galaxy S25, observed "Samsung S25 128GB" -> true
     * - target Galaxy S25, observed "Samsung telefon" -> false
     * - target Galaxy S25+, observed "Galaxy S25" -> false
     * - target Galaxy S25, observed "Galaxy S25 Ultra" -> false
     */
    public boolean provesConfiguredModel(
            String configuredModel,
            String observedText
    ) {
        List<String> targetTokens = tokenize(configuredModel);
        List<String> observedTokens = tokenize(observedText);

        if (findExplicitFamilyMismatch(
                targetTokens,
                observedTokens,
                configuredModel,
                observedText
        ).isPresent()) {
            return false;
        }

        Set<String> targetKeys = extractModelKeys(targetTokens);
        Set<String> observedKeys = extractModelKeys(observedTokens);

        if (targetKeys.isEmpty() || observedKeys.isEmpty()) {
            return false;
        }

        Set<String> sharedKeys = new HashSet<>(targetKeys);
        sharedKeys.retainAll(observedKeys);
        if (sharedKeys.isEmpty()) {
            return false;
        }

        Set<String> targetVariants = extractVariants(targetTokens);
        Set<String> observedVariants = extractVariants(observedTokens);

        if (!observedVariants.containsAll(targetVariants)) {
            return false;
        }

        Set<String> unexpectedObservedVariants =
                new LinkedHashSet<>(observedVariants);
        unexpectedObservedVariants.removeAll(targetVariants);

        return unexpectedObservedVariants.isEmpty();
    }

    private Optional<String> findExplicitFamilyMismatch(
            List<String> targetTokens,
            List<String> observedTokens,
            String configuredModel,
            String observedText
    ) {
        Set<String> targetFamilies = extractExplicitProductFamilies(targetTokens);
        Set<String> observedFamilies = extractExplicitProductFamilies(observedTokens);

        if (observedFamilies.isEmpty()) {
            return Optional.empty();
        }

        Set<String> unexpectedFamilies = new LinkedHashSet<>(observedFamilies);
        unexpectedFamilies.removeAll(targetFamilies);

        if (unexpectedFamilies.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(
                "observed text contains explicit product family "
                        + unexpectedFamilies
                        + " that is not part of configured model '"
                        + normalizeVisibleText(configuredModel)
                        + "' (observed='"
                        + normalizeVisibleText(observedText)
                        + "')"
        );
    }

    private Set<String> extractExplicitProductFamilies(List<String> tokens) {
        Set<String> families = new LinkedHashSet<>();
        for (String token : tokens) {
            if (EXPLICIT_PRODUCT_FAMILY_TOKENS.contains(token)) {
                families.add(token);
            }
        }
        return families;
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

        String[] rawTokens = normalized.split("\\s+");
        List<String> result = new ArrayList<>();

        for (int index = 0; index < rawTokens.length; index++) {
            String token = rawTokens[index];

            /* "s 25" -> "s25", "a 55" -> "a55". */
            if (token.matches("^[a-z]$")
                    && index + 1 < rawTokens.length
                    && rawTokens[index + 1].matches("^\\d+[a-z]*$")) {
                result.add(token + rawTokens[index + 1]);
                index++;
                continue;
            }

            result.add(token);
        }

        return result;
    }

    private String normalizeForMatching(String value) {
        if (value == null) {
            return "";
        }

        String withPlus = value
                .replace("+", " plus ")
                .replace("＋", " plus ");
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
