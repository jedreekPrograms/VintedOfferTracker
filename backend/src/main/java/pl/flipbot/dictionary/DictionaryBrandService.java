package pl.flipbot.dictionary;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.flipbot.dictionary.dto.CreateDictionaryBrandRequest;
import pl.flipbot.dictionary.dto.DictionaryBrandResponse;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DictionaryBrandService {

    private final DictionaryBrandRepository
            dictionaryBrandRepository;

    @Transactional(
            readOnly = true
    )
    public List<DictionaryBrandResponse> getAllBrands() {

        return dictionaryBrandRepository
                .findAllByOrderByNameAsc()
                .stream()
                .map(
                        this::map
                )
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

        if (dictionaryBrandRepository
                .existsByNameIgnoreCase(
                        normalizedName
                )) {

            throw new DictionaryEntryAlreadyExistsException(
                    "Brand already exists: "
                            + normalizedName
            );

        }

        DictionaryBrand brand =
                DictionaryBrand.builder()
                        .name(
                                normalizedName
                        )
                        .build();

        try {

            DictionaryBrand savedBrand =
                    dictionaryBrandRepository.saveAndFlush(
                            brand
                    );

            return map(
                    savedBrand
            );

        } catch (DataIntegrityViolationException exception) {

            /*
             * To dodatkowe zabezpieczenie na wypadek, gdyby dwa żądania
             * próbowały dodać tę samą markę w tym samym momencie.
             */
            throw new DictionaryEntryAlreadyExistsException(
                    "Brand already exists: "
                            + normalizedName
            );

        }

    }

    private String normalizeName(
            String name
    ) {

        if (name == null) {

            throw new IllegalArgumentException(
                    "Brand name cannot be null"
            );

        }

        /*
         * Usuwamy spacje z początku i końca oraz zamieniamy wiele
         * kolejnych spacji na jedną.
         *
         * "  Samsung   Electronics  "
         * zamieni się na:
         * "Samsung Electronics"
         */
        return name
                .trim()
                .replaceAll(
                        "\\s+",
                        " "
                );

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