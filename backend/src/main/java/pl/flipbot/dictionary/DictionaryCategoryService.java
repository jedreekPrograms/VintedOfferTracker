package pl.flipbot.dictionary;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.flipbot.bot.configuration.BotConfiguration;
import pl.flipbot.bot.configuration.BotConfigurationRepository;
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

    private final BotConfigurationRepository
            botConfigurationRepository;

    private final DictionaryUsageGuard
            dictionaryUsageGuard;


    @Transactional(readOnly = true)
    public List<DictionaryCategoryResponse> getAllCategories() {

        return dictionaryCategoryRepository
                .findAllByOrderByPathAsc()
                .stream()
                .map(this::map)
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


        if (
                dictionaryCategoryRepository
                        .existsByPathIgnoreCase(
                                normalizedPath
                        )
        ) {

            throw new DictionaryEntryAlreadyExistsException(
                    "Category already exists: "
                            + normalizedPath
            );
        }


        DictionaryCategory category =
                DictionaryCategory.builder()
                        .name(
                                lastPathElement(
                                        normalizedCategoryPath
                                )
                        )
                        .path(
                                normalizedPath
                        )
                        .build();


        try {

            return map(
                    dictionaryCategoryRepository
                            .saveAndFlush(
                                    category
                            )
            );

        } catch (DataIntegrityViolationException exception) {

            throw new DictionaryEntryAlreadyExistsException(
                    "Category already exists: "
                            + normalizedPath
            );
        }
    }


    @Transactional
    public DictionaryCategoryResponse updateCategory(
            Long categoryId,
            CreateDictionaryCategoryRequest request
    ) {

        DictionaryCategory category =
                dictionaryCategoryRepository
                        .findById(
                                categoryId
                        )
                        .orElseThrow(
                                () ->
                                        new DictionaryEntryNotFoundException(
                                                "Category was not found: "
                                                        + categoryId
                                        )
                        );


        List<String> oldCategoryPath =
                splitPath(
                        category.getPath()
                );

        List<String> normalizedCategoryPath =
                normalizeCategoryPath(
                        request.getCategoryPath()
                );

        String normalizedPath =
                String.join(
                        PATH_SEPARATOR,
                        normalizedCategoryPath
                );


        if (
                dictionaryCategoryRepository
                        .existsByPathIgnoreCaseAndIdNot(
                                normalizedPath,
                                categoryId
                        )
        ) {

            throw new DictionaryEntryAlreadyExistsException(
                    "Category already exists: "
                            + normalizedPath
            );
        }


        List<BotConfiguration> affectedConfigurations =
                findConfigurationsUsingCategory(
                        oldCategoryPath
                );


        dictionaryUsageGuard
                .ensureConfigurationsCanBeUpdated(
                        affectedConfigurations
                );


        category.setName(
                lastPathElement(
                        normalizedCategoryPath
                )
        );

        category.setPath(
                normalizedPath
        );


        for (
                BotConfiguration configuration
                : affectedConfigurations
        ) {

            configuration.setCategoryPath(
                    List.copyOf(
                            normalizedCategoryPath
                    )
            );
        }


        try {

            dictionaryCategoryRepository.flush();

        } catch (DataIntegrityViolationException exception) {

            throw new DictionaryEntryAlreadyExistsException(
                    "Category already exists: "
                            + normalizedPath
            );
        }


        return map(
                category
        );
    }


    @Transactional
    public void deleteCategory(
            Long categoryId
    ) {

        DictionaryCategory category =
                dictionaryCategoryRepository
                        .findById(
                                categoryId
                        )
                        .orElseThrow(
                                () ->
                                        new DictionaryEntryNotFoundException(
                                                "Category was not found: "
                                                        + categoryId
                                        )
                        );


        List<BotConfiguration> affectedConfigurations =
                findConfigurationsUsingCategory(
                        splitPath(
                                category.getPath()
                        )
                );


        dictionaryUsageGuard
                .ensureEntryIsNotUsed(
                        affectedConfigurations,
                        "Category '"
                                + category.getPath()
                                + "'"
                );


        dictionaryCategoryRepository.delete(
                category
        );
    }


    private List<BotConfiguration> findConfigurationsUsingCategory(
            List<String> categoryPath
    ) {

        return botConfigurationRepository
                .findAll()
                .stream()
                .filter(
                        configuration ->
                                categoryPathsEqual(
                                        configuration.getCategoryPath(),
                                        categoryPath
                                )
                )
                .toList();
    }


    private boolean categoryPathsEqual(
            List<String> left,
            List<String> right
    ) {

        if (
                left == null
                        || right == null
                        || left.size()
                        != right.size()
        ) {

            return false;
        }


        for (
                int index = 0;
                index < left.size();
                index++
        ) {

            if (
                    !normalizePathElement(
                            left.get(index)
                    ).equalsIgnoreCase(
                            normalizePathElement(
                                    right.get(index)
                            )
                    )
            ) {

                return false;
            }
        }


        return true;
    }


    private List<String> normalizeCategoryPath(
            List<String> categoryPath
    ) {

        if (
                categoryPath == null
                        || categoryPath.isEmpty()
        ) {

            throw new IllegalArgumentException(
                    "Category path cannot be empty"
            );
        }


        return categoryPath
                .stream()
                .map(this::normalizePathElement)
                .toList();
    }


    private String normalizePathElement(
            String pathElement
    ) {

        if (
                pathElement == null
        ) {

            throw new IllegalArgumentException(
                    "Category path element cannot be null"
            );
        }


        String normalizedElement =
                pathElement
                        .trim()
                        .replaceAll(
                                "\\s+",
                                " "
                        );


        if (
                normalizedElement.isBlank()
        ) {

            throw new IllegalArgumentException(
                    "Category path element cannot be blank"
            );
        }


        if (
                normalizedElement.contains(
                        ">"
                )
        ) {

            throw new IllegalArgumentException(
                    "Category path element cannot contain the '>' character: "
                            + normalizedElement
            );
        }


        return normalizedElement;
    }


    private String lastPathElement(
            List<String> categoryPath
    ) {

        return categoryPath.get(
                categoryPath.size() - 1
        );
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

        if (
                path == null
                        || path.isBlank()
        ) {

            throw new IllegalStateException(
                    "Stored category path cannot be blank"
            );
        }


        return Arrays.stream(
                        path.split(
                                "\\s*>\\s*"
                        )
                )
                .map(String::trim)
                .filter(
                        element ->
                                !element.isBlank()
                )
                .toList();
    }
}
