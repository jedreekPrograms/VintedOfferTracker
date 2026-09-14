package pl.flipbot.bot.configuration;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.flipbot.dictionary.DictionaryCategory;
import pl.flipbot.dictionary.DictionaryModel;
import pl.flipbot.dictionary.DictionaryModelRepository;

import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
public class BotTargetDictionaryCompatibilityGuard {

    private static final String PATH_SEPARATOR_REGEX = "\\s*>\\s*";

    private final DictionaryModelRepository dictionaryModelRepository;

    public void validate(
            List<String> categoryPath,
            String brand,
            TargetMode targetMode,
            String model,
            String searchQuery
    ) {
        if (targetMode == null || brand == null || brand.isBlank()) {
            return;
        }

        String targetName = switch (targetMode) {
            case VINTED_MODEL -> model;
            case SEARCH_QUERY -> searchQuery;
        };

        if (targetName == null || targetName.isBlank()) {
            return;
        }

        dictionaryModelRepository
                .findFirstByBrand_NameIgnoreCaseAndNameIgnoreCase(
                        normalizeText(brand),
                        normalizeText(targetName)
                )
                .ifPresent(dictionaryModel -> validateKnownModel(
                        categoryPath,
                        targetMode,
                        dictionaryModel
                ));
    }

    private void validateKnownModel(
            List<String> requestedCategoryPath,
            TargetMode requestedTargetMode,
            DictionaryModel dictionaryModel
    ) {
        TargetMode dictionaryTargetMode = dictionaryModel.getTargetMode() == null
                ? TargetMode.VINTED_MODEL
                : dictionaryModel.getTargetMode();

        if (dictionaryTargetMode != requestedTargetMode) {
            throw new IllegalArgumentException(
                    "Wybrany model słownikowy „"
                            + dictionaryModel.getName()
                            + "” ma tryb "
                            + dictionaryTargetMode
                            + ", a produkt próbuje użyć "
                            + requestedTargetMode
                            + "."
            );
        }

        DictionaryCategory dictionaryCategory = dictionaryModel.getCategory();
        if (dictionaryCategory == null) {
            // Legacy dictionary models may not have category metadata yet.
            return;
        }

        List<String> expectedCategoryPath = splitCategoryPath(
                dictionaryCategory.getPath()
        );

        if (!categoryPathsEqual(expectedCategoryPath, requestedCategoryPath)) {
            throw new IllegalArgumentException(
                    "Wybrany model słownikowy „"
                            + dictionaryModel.getName()
                            + "” należy do kategorii „"
                            + String.join(" → ", expectedCategoryPath)
                            + "”, a produkt ma kategorię „"
                            + displayCategoryPath(requestedCategoryPath)
                            + "”."
            );
        }
    }

    private List<String> splitCategoryPath(String path) {
        if (path == null || path.isBlank()) {
            return List.of();
        }

        return Arrays.stream(path.split(PATH_SEPARATOR_REGEX))
                .map(String::trim)
                .filter(element -> !element.isBlank())
                .toList();
    }

    private boolean categoryPathsEqual(
            List<String> left,
            List<String> right
    ) {
        if (left == null || right == null || left.size() != right.size()) {
            return false;
        }

        for (int index = 0; index < left.size(); index++) {
            if (!normalizeText(left.get(index)).equalsIgnoreCase(
                    normalizeText(right.get(index))
            )) {
                return false;
            }
        }

        return true;
    }

    private String displayCategoryPath(List<String> path) {
        if (path == null || path.isEmpty()) {
            return "brak";
        }
        return String.join(" → ", path);
    }

    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }
}
