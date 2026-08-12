package pl.flipbot.dictionary;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.flipbot.bot.configuration.BotConfiguration;
import pl.flipbot.bot.configuration.BotConfigurationRepository;
import pl.flipbot.dictionary.dto.CreateDictionaryModelRequest;
import pl.flipbot.dictionary.dto.DictionaryModelResponse;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DictionaryModelService {

    private final DictionaryModelRepository dictionaryModelRepository;

    private final DictionaryBrandRepository dictionaryBrandRepository;

    private final BotConfigurationRepository botConfigurationRepository;

    private final DictionaryUsageGuard dictionaryUsageGuard;


    @Transactional(readOnly = true)
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
                .map(this::map)
                .toList();
    }


    @Transactional
    public DictionaryModelResponse createModel(
            Long brandId,
            CreateDictionaryModelRequest request
    ) {

        DictionaryBrand brand =
                getBrand(
                        brandId
                );

        String normalizedName =
                normalizeName(
                        request.getName()
                );


        if (
                dictionaryModelRepository
                        .existsByBrand_IdAndNameIgnoreCase(
                                brandId,
                                normalizedName
                        )
        ) {

            throw new DictionaryEntryAlreadyExistsException(
                    "Model already exists for brand "
                            + brand.getName()
                            + ": "
                            + normalizedName
            );
        }


        DictionaryModel model =
                DictionaryModel.builder()
                        .name(normalizedName)
                        .brand(brand)
                        .build();


        try {

            return map(
                    dictionaryModelRepository
                            .saveAndFlush(
                                    model
                            )
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


    @Transactional
    public DictionaryModelResponse updateModel(
            Long brandId,
            Long modelId,
            CreateDictionaryModelRequest request
    ) {

        DictionaryBrand brand =
                getBrand(
                        brandId
                );

        DictionaryModel model =
                dictionaryModelRepository
                        .findById(
                                modelId
                        )
                        .orElseThrow(
                                () ->
                                        new DictionaryEntryNotFoundException(
                                                "Model was not found: "
                                                        + modelId
                                        )
                        );


        if (
                !model.getBrand().getId()
                        .equals(
                                brandId
                        )
        ) {

            throw new DictionaryEntryNotFoundException(
                    "Model "
                            + modelId
                            + " does not belong to brand "
                            + brandId
            );
        }


        String oldName =
                model.getName();

        String normalizedName =
                normalizeName(
                        request.getName()
                );


        if (
                dictionaryModelRepository
                        .existsByBrand_IdAndNameIgnoreCaseAndIdNot(
                                brandId,
                                normalizedName,
                                modelId
                        )
        ) {

            throw new DictionaryEntryAlreadyExistsException(
                    "Model already exists for brand "
                            + brand.getName()
                            + ": "
                            + normalizedName
            );
        }


        List<BotConfiguration> affectedConfigurations =
                botConfigurationRepository
                        .findAllByBrandIgnoreCaseAndModelIgnoreCase(
                                brand.getName(),
                                oldName
                        );


        dictionaryUsageGuard
                .ensureConfigurationsCanBeUpdated(
                        affectedConfigurations
                );


        model.setName(
                normalizedName
        );


        for (
                BotConfiguration configuration
                : affectedConfigurations
        ) {

            configuration.setModel(
                    normalizedName
            );
        }


        try {

            dictionaryModelRepository.flush();

        } catch (DataIntegrityViolationException exception) {

            throw new DictionaryEntryAlreadyExistsException(
                    "Model already exists for brand "
                            + brand.getName()
                            + ": "
                            + normalizedName
            );
        }


        return map(
                model
        );
    }


    @Transactional
    public void deleteModel(
            Long brandId,
            Long modelId
    ) {

        DictionaryBrand brand =
                getBrand(
                        brandId
                );

        DictionaryModel model =
                dictionaryModelRepository
                        .findById(
                                modelId
                        )
                        .orElseThrow(
                                () ->
                                        new DictionaryEntryNotFoundException(
                                                "Model was not found: "
                                                        + modelId
                                        )
                        );


        if (
                !model.getBrand().getId()
                        .equals(
                                brandId
                        )
        ) {

            throw new DictionaryEntryNotFoundException(
                    "Model "
                            + modelId
                            + " does not belong to brand "
                            + brandId
            );
        }


        List<BotConfiguration> affectedConfigurations =
                botConfigurationRepository
                        .findAllByBrandIgnoreCaseAndModelIgnoreCase(
                                brand.getName(),
                                model.getName()
                        );


        dictionaryUsageGuard
                .ensureEntryIsNotUsed(
                        affectedConfigurations,
                        "Model '"
                                + model.getName()
                                + "'"
                );


        dictionaryModelRepository.delete(
                model
        );
    }


    private DictionaryBrand getBrand(
            Long brandId
    ) {

        return dictionaryBrandRepository
                .findById(
                        brandId
                )
                .orElseThrow(
                        () ->
                                new DictionaryEntryNotFoundException(
                                        "Brand was not found: "
                                                + brandId
                                )
                );
    }


    private void validateBrandExists(
            Long brandId
    ) {

        if (
                !dictionaryBrandRepository.existsById(
                        brandId
                )
        ) {

            throw new DictionaryEntryNotFoundException(
                    "Brand was not found: "
                            + brandId
            );
        }
    }


    private String normalizeName(
            String name
    ) {

        if (
                name == null
        ) {

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


        if (
                normalizedName.isBlank()
        ) {

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
