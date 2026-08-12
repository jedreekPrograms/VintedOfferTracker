package pl.flipbot.dictionary;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.flipbot.bot.configuration.BotConfiguration;
import pl.flipbot.bot.configuration.BotConfigurationRepository;
import pl.flipbot.dictionary.dto.CreateDictionaryBrandRequest;
import pl.flipbot.dictionary.dto.DictionaryBrandResponse;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DictionaryBrandService {

    private final DictionaryBrandRepository
            dictionaryBrandRepository;

    private final DictionaryModelRepository
            dictionaryModelRepository;

    private final BotConfigurationRepository
            botConfigurationRepository;

    private final DictionaryUsageGuard
            dictionaryUsageGuard;


    @Transactional(readOnly = true)
    public List<DictionaryBrandResponse> getAllBrands() {

        return dictionaryBrandRepository
                .findAllByOrderByNameAsc()
                .stream()
                .map(this::map)
                .toList();
    }


    @Transactional
    public DictionaryBrandResponse createBrand(
            CreateDictionaryBrandRequest request
    ) {

        String normalizedName =
                normalizeName(
                        request.getName()
                );


        if (
                dictionaryBrandRepository
                        .existsByNameIgnoreCase(
                                normalizedName
                        )
        ) {

            throw new DictionaryEntryAlreadyExistsException(
                    "Brand already exists: "
                            + normalizedName
            );
        }


        DictionaryBrand brand =
                DictionaryBrand.builder()
                        .name(normalizedName)
                        .build();


        try {

            return map(
                    dictionaryBrandRepository
                            .saveAndFlush(
                                    brand
                            )
            );

        } catch (DataIntegrityViolationException exception) {

            throw new DictionaryEntryAlreadyExistsException(
                    "Brand already exists: "
                            + normalizedName
            );
        }
    }


    @Transactional
    public DictionaryBrandResponse updateBrand(
            Long brandId,
            CreateDictionaryBrandRequest request
    ) {

        DictionaryBrand brand =
                dictionaryBrandRepository
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


        String oldName =
                brand.getName();

        String normalizedName =
                normalizeName(
                        request.getName()
                );


        if (
                dictionaryBrandRepository
                        .existsByNameIgnoreCaseAndIdNot(
                                normalizedName,
                                brandId
                        )
        ) {

            throw new DictionaryEntryAlreadyExistsException(
                    "Brand already exists: "
                            + normalizedName
            );
        }


        List<BotConfiguration> affectedConfigurations =
                botConfigurationRepository
                        .findAllByBrandIgnoreCase(
                                oldName
                        );


        dictionaryUsageGuard
                .ensureConfigurationsCanBeUpdated(
                        affectedConfigurations
                );


        brand.setName(
                normalizedName
        );


        for (
                BotConfiguration configuration
                : affectedConfigurations
        ) {

            configuration.setBrand(
                    normalizedName
            );
        }


        try {

            dictionaryBrandRepository.flush();

        } catch (DataIntegrityViolationException exception) {

            throw new DictionaryEntryAlreadyExistsException(
                    "Brand already exists: "
                            + normalizedName
            );
        }


        return map(
                brand
        );
    }


    @Transactional
    public void deleteBrand(
            Long brandId
    ) {

        DictionaryBrand brand =
                dictionaryBrandRepository
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


        List<BotConfiguration> affectedConfigurations =
                botConfigurationRepository
                        .findAllByBrandIgnoreCase(
                                brand.getName()
                        );


        dictionaryUsageGuard
                .ensureEntryIsNotUsed(
                        affectedConfigurations,
                        "Brand '"
                                + brand.getName()
                                + "'"
                );


        if (
                dictionaryModelRepository
                        .existsByBrand_Id(
                                brandId
                        )
        ) {

            throw new IllegalStateException(
                    "Brand cannot be deleted while it still has dictionary models."
            );
        }


        dictionaryBrandRepository.delete(
                brand
        );
    }


    private String normalizeName(
            String name
    ) {

        if (
                name == null
        ) {

            throw new IllegalArgumentException(
                    "Brand name cannot be null"
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
                    "Brand name cannot be blank"
            );
        }


        return normalizedName;
    }


    private DictionaryBrandResponse map(
            DictionaryBrand brand
    ) {

        return new DictionaryBrandResponse(
                brand.getId(),
                brand.getName()
        );
    }
}
