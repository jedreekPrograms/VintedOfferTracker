package pl.flipbot.analytics;

import org.springframework.stereotype.Component;
import pl.flipbot.dictionary.DictionaryModel;
import pl.flipbot.listing.Listing;
import pl.flipbot.listing.ListingHistoryMetadata;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Resolves the dictionary model for history analytics.
 *
 * New listings carry an immutable productTargetLabel snapshot and always use
 * that first. Legacy buy-candidate rows created before the snapshot existed
 * may have only a marketplace title. For those rows we use a conservative
 * title fallback:
 *
 *  - brand + model must occur as a whole normalized phrase in the title,
 *  - the most specific (longest) matching model wins,
 *  - equally-specific ambiguous matches are rejected instead of guessed.
 *
 * This keeps e.g. "Galaxy S26 Ultra" out of plain "Galaxy S26" while allowing
 * old exact-title purchases such as "Samsung Galaxy S26" to participate in
 * S26 statistics.
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
                .map(model -> candidate(model, title))
                .flatMap(Optional::stream)
                .sorted(
                        Comparator.comparingInt(Candidate::specificity)
                                .reversed()
                )
                .toList();

        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        Candidate best = candidates.getFirst();

        long equallySpecific = candidates.stream()
                .filter(candidate ->
                        candidate.specificity() == best.specificity()
                )
                .count();

        if (equallySpecific > 1) {
            return Optional.empty();
        }

        return Optional.of(best.modelId());
    }

    private Optional<Candidate> candidate(
            DictionaryModel model,
            String canonicalTitle
    ) {
        if (model == null
                || model.getId() == null
                || model.getBrand() == null) {
            return Optional.empty();
        }

        String brandAndModel = canonical(
                model.getBrand().getName() + " " + model.getName()
        );

        if (brandAndModel == null
                || !containsPhrase(canonicalTitle, brandAndModel)) {
            return Optional.empty();
        }

        return Optional.of(
                new Candidate(
                        model.getId(),
                        canonical(model.getName()).length()
                )
        );
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
            int specificity
    ) {
    }
}
