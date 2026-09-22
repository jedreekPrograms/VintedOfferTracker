package pl.flipbot.analytics;

import org.springframework.stereotype.Component;
import pl.flipbot.dictionary.DictionaryModel;
import pl.flipbot.listing.Listing;
import pl.flipbot.listing.ListingHistoryMetadata;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves a historical buy-candidate to one dictionary model.
 *
 * Priority:
 *  1. immutable productTargetLabel snapshot,
 *  2. legacy listing title matched against the COMPLETE model dictionary.
 *
 * Legacy matching is intentionally conservative:
 *  - longer/more specific variants win (S26 Ultra before S26),
 *  - brand + model beats model-only for otherwise equal matches,
 *  - model-only fallback is allowed for old titles which omitted the brand,
 *  - year-in-parentheses aliases allow e.g. "iPad 10.9" to match
 *    "iPad 10.9 (2022)",
 *  - equally strong matches are treated as ambiguous instead of guessed.
 */
@Component
public class HistoryModelResolver {

    public Optional<Long> resolveModelId(
            Listing listing,
            List<DictionaryModel> models
    ) {
        if (listing == null || models == null || models.isEmpty()) {
            return Optional.empty();
        }

        Optional<Long> snapshotMatch = resolveFromSnapshot(listing, models);
        if (snapshotMatch.isPresent()) {
            return snapshotMatch;
        }

        return resolveFromLegacyTitle(listing, models);
    }

    public Optional<DictionaryModel> resolveModel(
            Listing listing,
            List<DictionaryModel> models
    ) {
        Optional<Long> modelId = resolveModelId(listing, models);

        if (modelId.isEmpty()) {
            return Optional.empty();
        }

        return models.stream()
                .filter(model -> modelId.get().equals(model.getId()))
                .findFirst();
    }

    private Optional<Long> resolveFromSnapshot(
            Listing listing,
            List<DictionaryModel> models
    ) {
        String snapshot = ListingHistoryMetadata.normalizeLabel(
                listing.getProductTargetLabel()
        );

        if (snapshot == null) {
            return Optional.empty();
        }

        return models.stream()
                .filter(model ->
                        snapshot.equals(
                                ListingHistoryMetadata.normalizeLabel(
                                        ListingHistoryMetadata.modelLabel(
                                                model.getBrand().getName(),
                                                model.getName()
                                        )
                                )
                        )
                )
                .map(DictionaryModel::getId)
                .findFirst();
    }

    private Optional<Long> resolveFromLegacyTitle(
            Listing listing,
            List<DictionaryModel> models
    ) {
        String title = canonical(listing.getTitle());

        if (title == null) {
            return Optional.empty();
        }

        List<Candidate> candidates = models.stream()
                .flatMap(model -> candidates(model, title).stream())
                .sorted(
                        Comparator.comparingInt(Candidate::specificity)
                                .reversed()
                                .thenComparing(
                                        Comparator.comparingInt(
                                                Candidate::confidence
                                        ).reversed()
                                )
                )
                .toList();

        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        Candidate best = candidates.getFirst();

        long equallyStrongDifferentModels = candidates.stream()
                .filter(candidate ->
                        candidate.specificity() == best.specificity()
                                && candidate.confidence() == best.confidence()
                                && !candidate.modelId().equals(best.modelId())
                )
                .count();

        if (equallyStrongDifferentModels > 0) {
            return Optional.empty();
        }

        return Optional.of(best.modelId());
    }

    private List<Candidate> candidates(
            DictionaryModel model,
            String canonicalTitle
    ) {
        if (model == null
                || model.getId() == null
                || model.getBrand() == null
                || model.getName() == null) {
            return List.of();
        }

        String brand = canonical(model.getBrand().getName());

        if (brand == null) {
            return List.of();
        }

        List<Candidate> result = new ArrayList<>();

        for (String alias : modelAliases(model.getName())) {
            if (alias == null || alias.isBlank()) {
                continue;
            }

            int specificity = alias.length();

            if (containsPhrase(
                    canonicalTitle,
                    brand + " " + alias
            )) {
                result.add(
                        new Candidate(
                                model.getId(),
                                specificity,
                                2
                        )
                );
                continue;
            }

            if (containsPhrase(canonicalTitle, alias)) {
                result.add(
                        new Candidate(
                                model.getId(),
                                specificity,
                                1
                        )
                );
            }
        }

        return result;
    }

    private Set<String> modelAliases(String rawModelName) {
        Set<String> aliases = new LinkedHashSet<>();

        String canonicalFull = canonical(rawModelName);
        if (canonicalFull != null) {
            aliases.add(canonicalFull);
        }

        /*
         * Historical marketplace titles often omit the generation year which
         * the dictionary keeps in parentheses, e.g. "iPad 10.9 (2022)".
         */
        String withoutParenthesizedYear = rawModelName.replaceAll(
                "\\s*\\((?:19|20)\\d{2}\\)\\s*$",
                ""
        );

        String canonicalWithoutYear = canonical(withoutParenthesizedYear);
        if (canonicalWithoutYear != null) {
            aliases.add(canonicalWithoutYear);
        }

        return aliases;
    }

    private boolean containsPhrase(
            String haystack,
            String needle
    ) {
        return (" " + haystack + " ")
                .contains(" " + needle + " ");
    }

    private String canonical(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String ascii = Normalizer.normalize(
                        value,
                        Normalizer.Form.NFKD
                )
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replace("+", " plus ")
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim()
                .replaceAll("\\s+", " ");

        return ascii.isBlank() ? null : ascii;
    }

    private record Candidate(
            Long modelId,
            int specificity,
            int confidence
    ) {
    }
}
