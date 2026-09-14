package pl.flipbot.bot.configuration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.flipbot.dictionary.DictionaryBrand;
import pl.flipbot.dictionary.DictionaryCategory;
import pl.flipbot.dictionary.DictionaryModel;
import pl.flipbot.dictionary.DictionaryModelRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BotTargetDictionaryCompatibilityGuardTest {

    private DictionaryModelRepository modelRepository;
    private BotTargetDictionaryCompatibilityGuard guard;

    @BeforeEach
    void setUp() {
        modelRepository = mock(DictionaryModelRepository.class);
        guard = new BotTargetDictionaryCompatibilityGuard(modelRepository);
    }

    @Test
    void acceptsKnownModelInMatchingCategory() {
        whenModel("Samsung", "Galaxy S25", model(
                "Galaxy S25",
                TargetMode.VINTED_MODEL,
                "Elektronika > Telefony"
        ));

        assertDoesNotThrow(() -> guard.validate(
                List.of("Elektronika", "Telefony"),
                " Samsung ",
                TargetMode.VINTED_MODEL,
                " Galaxy   S25 ",
                null
        ));
    }

    @Test
    void rejectsKnownModelFromDifferentCategory() {
        whenModel("Samsung", "Galaxy Tab S10", model(
                "Galaxy Tab S10",
                TargetMode.VINTED_MODEL,
                "Elektronika > Tablety"
        ));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> guard.validate(
                        List.of("Elektronika", "Telefony"),
                        "Samsung",
                        TargetMode.VINTED_MODEL,
                        "Galaxy Tab S10",
                        null
                )
        );

        assertTrue(exception.getMessage().contains("Tablety"));
        assertTrue(exception.getMessage().contains("Telefony"));
    }

    @Test
    void searchQueryUsesDictionaryTargetNameForCompatibilityCheck() {
        whenModel("Sony", "PlayStation 5 Slim", model(
                "PlayStation 5 Slim",
                TargetMode.SEARCH_QUERY,
                "Elektronika > Konsole"
        ));

        assertDoesNotThrow(() -> guard.validate(
                List.of("Elektronika", "Konsole"),
                "Sony",
                TargetMode.SEARCH_QUERY,
                null,
                "PlayStation 5 Slim"
        ));
    }

    @Test
    void rejectsKnownModelWithDifferentTargetMode() {
        whenModel("Sony", "PlayStation 5 Slim", model(
                "PlayStation 5 Slim",
                TargetMode.SEARCH_QUERY,
                "Elektronika > Konsole"
        ));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> guard.validate(
                        List.of("Elektronika", "Konsole"),
                        "Sony",
                        TargetMode.VINTED_MODEL,
                        "PlayStation 5 Slim",
                        null
                )
        );

        assertTrue(exception.getMessage().contains("SEARCH_QUERY"));
        assertTrue(exception.getMessage().contains("VINTED_MODEL"));
    }

    @Test
    void legacyModelWithoutCategoryMetadataRemainsAllowed() {
        DictionaryModel legacy = model(
                "iPhone 13",
                TargetMode.VINTED_MODEL,
                null
        );
        whenModel("Apple", "iPhone 13", legacy);

        assertDoesNotThrow(() -> guard.validate(
                List.of("Elektronika", "Telefony"),
                "Apple",
                TargetMode.VINTED_MODEL,
                "iPhone 13",
                null
        ));
    }

    @Test
    void unknownDictionaryTargetDoesNotBlockLegacyConfiguration() {
        when(modelRepository.findFirstByBrand_NameIgnoreCaseAndNameIgnoreCase(
                "Legacy Brand",
                "Legacy Model"
        )).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> guard.validate(
                List.of("Inne"),
                "Legacy Brand",
                TargetMode.VINTED_MODEL,
                "Legacy Model",
                null
        ));
    }

    private void whenModel(
            String brand,
            String targetName,
            DictionaryModel model
    ) {
        when(modelRepository.findFirstByBrand_NameIgnoreCaseAndNameIgnoreCase(
                brand,
                targetName
        )).thenReturn(Optional.of(model));
    }

    private DictionaryModel model(
            String name,
            TargetMode targetMode,
            String categoryPath
    ) {
        DictionaryBrand brand = DictionaryBrand.builder()
                .id(1L)
                .name("brand")
                .build();

        DictionaryCategory category = categoryPath == null
                ? null
                : DictionaryCategory.builder()
                .id(2L)
                .name(categoryPath.substring(categoryPath.lastIndexOf('>') + 1).trim())
                .path(categoryPath)
                .build();

        return DictionaryModel.builder()
                .id(3L)
                .name(name)
                .brand(brand)
                .category(category)
                .targetMode(targetMode)
                .build();
    }
}
