package pl.flipbot.dictionary;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.flipbot.dictionary.dto.CreateDictionaryModelRequest;
import pl.flipbot.dictionary.dto.DictionaryModelResponse;

import java.util.List;


@Service
@RequiredArgsConstructor
public class DictionaryModelService {

    private final DictionaryModelRepository dictionaryModelRepository;

    private final DictionaryBrandRepository dictionaryBrandRepository;

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

        if(!dictionaryModelRepository.existsById(
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

    private DictionaryModelResponse map(
            DictionaryModel model
    ) {

        return new DictionaryModelResponse(
                model.getId(),
                model.getName(),
                model.getBrand().getId(),
                model.getBrand().getName()
        );

    }

}
