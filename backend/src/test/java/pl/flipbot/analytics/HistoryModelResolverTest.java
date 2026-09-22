package pl.flipbot.analytics;

import org.junit.jupiter.api.Test;
import pl.flipbot.dictionary.DictionaryBrand;
import pl.flipbot.dictionary.DictionaryModel;
import pl.flipbot.listing.Listing;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoryModelResolverTest {

    private final HistoryModelResolver resolver =
            new HistoryModelResolver();

    @Test
    void usesImmutableSnapshotWhenAvailable() {
        DictionaryModel s26 = model(1L, "Samsung", "Galaxy S26");
        Listing listing = listing("Completely unrelated title");
        listing.setProductTargetLabel("Samsung → Galaxy S26");

        assertEquals(
                1L,
                resolver.resolveModelId(listing, List.of(s26)).orElseThrow()
        );
    }

    @Test
    void resolvesLegacyExactS26TitleWithoutSnapshot() {
        DictionaryModel s26 = model(1L, "Samsung", "Galaxy S26");
        Listing listing = listing("Samsung Galaxy S26");

        assertEquals(
                1L,
                resolver.resolveModelId(listing, List.of(s26)).orElseThrow()
        );
    }

    @Test
    void mostSpecificVariantWinsOverPlainS26() {
        DictionaryModel s26 = model(1L, "Samsung", "Galaxy S26");
        DictionaryModel ultra = model(2L, "Samsung", "Galaxy S26 Ultra");
        Listing listing = listing("Samsung Galaxy S26 Ultra 256 GB");

        assertEquals(
                2L,
                resolver.resolveModelId(
                        listing,
                        List.of(s26, ultra)
                ).orElseThrow()
        );
    }

    @Test
    void plusVariantDoesNotFallIntoPlainS26() {
        DictionaryModel s26 = model(1L, "Samsung", "Galaxy S26");
        DictionaryModel plus = model(3L, "Samsung", "Galaxy S26+");
        Listing listing = listing("Samsung Galaxy S26+ 512GB");

        assertEquals(
                3L,
                resolver.resolveModelId(
                        listing,
                        List.of(s26, plus)
                ).orElseThrow()
        );
    }

    @Test
    void resolvesModelWithoutBrandWhenUnique() {
        DictionaryModel s25fe = model(
                4L,
                "Samsung",
                "Galaxy S25 FE"
        );
        Listing listing = listing("Galaxy S25 FE 256GB");

        assertEquals(
                4L,
                resolver.resolveModelId(
                        listing,
                        List.of(s25fe)
                ).orElseThrow()
        );
    }

    @Test
    void resolvesIpadWhenLegacyTitleOmitsDictionaryYearSuffix() {
        DictionaryModel ipad = model(
                5L,
                "Apple",
                "iPad 10.9 (2022)"
        );
        Listing listing = listing("Apple iPad 10.9 64GB Wi-Fi");

        assertEquals(
                5L,
                resolver.resolveModelId(
                        listing,
                        List.of(ipad)
                ).orElseThrow()
        );
    }

    @Test
    void ambiguousModelOnlyMatchAcrossBrandsIsNotGuessed() {
        DictionaryModel alpha = model(
                6L,
                "Brand A",
                "Model X"
        );
        DictionaryModel beta = model(
                7L,
                "Brand B",
                "Model X"
        );
        Listing listing = listing("Model X 256GB");

        assertTrue(
                resolver.resolveModelId(
                        listing,
                        List.of(alpha, beta)
                ).isEmpty()
        );
    }

    @Test
    void unrelatedLegacyTitleIsNotGuessed() {
        DictionaryModel s26 = model(1L, "Samsung", "Galaxy S26");
        Listing listing = listing("Apple iPhone 16 Pro");

        assertTrue(
                resolver.resolveModelId(
                        listing,
                        List.of(s26)
                ).isEmpty()
        );
    }

    private Listing listing(String title) {
        return Listing.builder()
                .title(title)
                .build();
    }

    private DictionaryModel model(
            Long id,
            String brand,
            String name
    ) {
        return DictionaryModel.builder()
                .id(id)
                .brand(
                        DictionaryBrand.builder()
                                .id(id + 100)
                                .name(brand)
                                .build()
                )
                .name(name)
                .build();
    }
}