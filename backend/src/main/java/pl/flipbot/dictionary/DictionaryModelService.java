package pl.flipbot.dictionary;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.flipbot.bot.configuration.TargetMode;
import pl.flipbot.dictionary.dto.CreateDictionaryModelRequest;
import pl.flipbot.dictionary.dto.DictionaryModelResponse;

import java.util.Arrays;
import java.util.List;


@Service
@RequiredArgsConstructor
public class DictionaryModelService {

    private static final String PATH_SEPARATOR_REGEX = "\\s*>\\s*";

    private final DictionaryModelRepository dictionaryModelRepository;

    private final DictionaryBrandRepository dictionaryBrandRepository;

    private final DictionaryCategoryRepository dictionaryCategoryRepository;

    @Transactional(
            readOnly = true
    )
    public List<DictionaryModelResponse> getModelsByBrand(
            Long brandId
    ) {

        validateBrandExists(
                brandId
        );

        return dictionaryModelRepository
                .findAllByBrand_IdOrderByNameAsc(
                        brandId
                )
                .stream()
                .map(
                        this::map
                )
                .toList();

    }

    @Transactional
    public DictionaryModelResponse createModel(
            Long brandId,
            CreateDictionaryModelRequest request
    ) {

        DictionaryBrand brand =
                dictionaryBrandRepository
                        .findById(
                                brandId
                        )
                        .orElseThrow(
                                () -> new DictionaryEntryNotFoundException(
                                        "Brand was not found: "
                                                + brandId
                                )
                        );

        Long categoryId = request.getCategoryId();

        if (categoryId == null || categoryId <= 0) {
            throw new IllegalArgumentException(
                    "Dictionary model category is required."
            );
        }

        DictionaryCategory category =
                dictionaryCategoryRepository
                        .findById(categoryId)
                        .orElseThrow(
                                () -> new DictionaryEntryNotFoundException(
                                        "Category was not found: "
                                                + categoryId
                                )
                        );

        String normalizedName =
                normalizeName(
                        request.getName()
                );

        boolean modelAlreadyExists =
                dictionaryModelRepository
                        .existsByBrand_IdAndNameIgnoreCase(
                                brandId,
                                normalizedName
                        );

        if (modelAlreadyExists) {

            throw new DictionaryEntryAlreadyExistsException(
                    "Model already exists for brand "
                            + brand.getName()
                            + ": "
                            + normalizedName
            );

        }

        DictionaryModel model =
                DictionaryModel.builder()
                        .name(
                                normalizedName
                        )
                        .brand(
                                brand
                        )
                        .category(
                                category
                        )
                        .targetMode(
                                request.getTargetMode() == null
                                        ? TargetMode.VINTED_MODEL
                                        : request.getTargetMode()
                        )
                        .build();

        try {

            DictionaryModel savedModel =
                    dictionaryModelRepository
                            .saveAndFlush(
                                    model
                            );

            return map(
                    savedModel
            );

        } catch (DataIntegrityViolationException exception) {

            throw new DictionaryEntryAlreadyExistsException(
                    "Model already exists for brand "
                            + brand.getName()
                            + ": "
                            + normalizedName
            );

        }

    }

    private void validateBrandExists(
            Long brandId
    ) {

        if(!dictionaryBrandRepository.existsById(
                brandId
        )) {

            throw new DictionaryEntryNotFoundException(
                    "Brand was not found: "
                            + brandId
            );

        }

    }

    private String normalizeName(
            String name
    ) {

        if (name == null) {

            throw new IllegalArgumentException(
                    "Model name cannot be null"
            );

        }

        String normalizedName =
                name
                        .trim()
                        .replaceAll(
                                "\\s+",
                                " "
                        );

        if (normalizedName.isBlank()) {

            throw new IllegalArgumentException(
                    "Model name cannot be blank"
            );

        }

        return normalizedName;

    }

    DictionaryModelResponse map(
            DictionaryModel model
    ) {
        DictionaryCategory category = model.getCategory();

        return new DictionaryModelResponse(
                model.getId(),
                model.getName(),
                model.getBrand().getId(),
                model.getBrand().getName(),
                category == null ? null : category.getId(),
                category == null ? null : category.getName(),
                category == null ? null : category.getPath(),
                category == null
                        ? List.of()
                        : Arrays.stream(category.getPath().split(PATH_SEPARATOR_REGEX))
                        .map(String::trim)
                        .filter(element -> !element.isBlank())
                        .toList(),
                model.getTargetMode() == null
                        ? TargetMode.VINTED_MODEL
                        : model.getTargetMode(),
                model.getProposedOfferPrice(),
                model.getExpectedResalePrice()
        );

    }

}
