package pl.flipbot.dictionary;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.flipbot.dictionary.dto.CreateDictionaryCategoryRequest;
import pl.flipbot.dictionary.dto.DictionaryCategoryResponse;

import java.util.Arrays;
import java.util.List;
@Service
@RequiredArgsConstructor
public class DictionaryCategoryService {

    private static final String PATH_SEPARATOR =
            " > ";

    private final DictionaryCategoryRepository
            dictionaryCategoryRepository;


    @Transactional(
            readOnly = true
    )
    public List<DictionaryCategoryResponse> getAllCategories() {

        return dictionaryCategoryRepository
                .findAllByOrderByPathAsc()
                .stream()
                .map(
                        this::map
                )
                .toList();

    }

    @Transactional
    public DictionaryCategoryResponse createCategory(
            CreateDictionaryCategoryRequest request
    ) {

        List<String> normalizedCategoryPath =
                normalizeCategoryPath(
                        request.getCategoryPath()
                );

        String normalizedPath =
                String.join(
                        PATH_SEPARATOR,
                        normalizedCategoryPath
                );

        if (dictionaryCategoryRepository
                .existsByPathIgnoreCase(
                        normalizedPath
                )) {

            throw new DictionaryEntryAlreadyExistsException(
                    "Category already exists: "
                    + normalizedPath
            );

        }

        String categoryName =
                normalizedCategoryPath.get(normalizedCategoryPath.size() - 1
                );

        DictionaryCategory category =
                DictionaryCategory.builder()
                        .name(
                                categoryName
                        )
                        .path(
                                normalizedPath
                        )
                        .build();

        try {

            DictionaryCategory savedCategory =
                    dictionaryCategoryRepository
                            .saveAndFlush(
                                    category
                            );

            return map(
                    savedCategory
            );

        } catch (DataIntegrityViolationException exception) {

            throw new DictionaryEntryAlreadyExistsException(
                    "Category already exists: "
                    + normalizedPath
            );

        }

    }

    private List<String> normalizeCategoryPath(
            List<String> categoryPath
    ) {

        if (categoryPath == null
                || categoryPath.isEmpty()) {

            throw new IllegalArgumentException(
                    "Category path cannot be empty"
            );

        }

        return categoryPath.stream()
                .map(
                        this::normalizePathElement
                )
                .toList();
    }

    private String normalizePathElement(
            String pathElement
    ) {

        if (pathElement == null) {

            throw new IllegalArgumentException(
                    "Category path element cannot be null"
            );

        }

        String normalizedElement =
                pathElement
                        .trim()
                        .replaceAll("\\s+",
                                " "
                        );
        if (normalizedElement.isBlank()) {

            throw new IllegalArgumentException(
                    "Category path element cannot be blank"
            );

        }

        if (normalizedElement.contains(
                ">"
        )) {

            throw new IllegalArgumentException(
                    "Category path element cannot contain  the '>' character: "
                    + normalizedElement
            );

        }

        return normalizedElement;

    }

    private DictionaryCategoryResponse map(
            DictionaryCategory category
    ) {

        List<String> categoryPath =
                splitPath(
                        category.getPath()
                );

        return new DictionaryCategoryResponse(
                category.getId(),
                category.getName(),
                category.getPath(),
                categoryPath
        );

    }

    private List<String> splitPath(
            String path
    ) {

        if (path == null
                || path.isBlank()) {

            throw new IllegalStateException(
                    "Stored category path cannot be blank"
            );
        }

        return Arrays.stream(
                        path.split(
                           "\\s*>\\s*"
                        )
                )
                .map(
                        String::trim
                )
                .filter(
                        element -> !element.isBlank()
                )
                .toList();

    }
}
