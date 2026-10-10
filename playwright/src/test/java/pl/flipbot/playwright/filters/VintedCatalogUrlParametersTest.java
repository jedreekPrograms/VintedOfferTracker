package pl.flipbot.playwright.filters;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class VintedCatalogUrlParametersTest {

    @Test
    public void decodesExistingCatalogParametersAndSearchText() {
        assertEquals("3661", VintedCatalogUrlParameters.get(
                "https://www.vinted.pl/catalog?catalog%5B%5D=3661&order=newest_first",
                "catalog[]"
        ));
        assertEquals("Galaxy S25 FE", VintedCatalogUrlParameters.get(
                "https://www.vinted.pl/catalog?search_text=Galaxy+S25+FE#panel",
                "search_text"
        ));
    }

    @Test
    public void returnsFirstValueForRepeatedKeysLikeTheOriginalService() {
        assertEquals("first", VintedCatalogUrlParameters.get(
                "https://www.vinted.pl/catalog?search_text=first&search_text=second",
                "search_text"
        ));
    }

    @Test
    public void distinguishesMissingAndEmptyParameters() {
        assertEquals("", VintedCatalogUrlParameters.get(
                "https://www.vinted.pl/catalog?order&other=1", "order"
        ));
        assertEquals("", VintedCatalogUrlParameters.get(
                "https://www.vinted.pl/catalog?order=", "order"
        ));
        assertNull(VintedCatalogUrlParameters.get(
                "https://www.vinted.pl/catalog?brand_ids%5B%5D=109048", "order"
        ));
        assertNull(VintedCatalogUrlParameters.get(
                "https://www.vinted.pl/catalog", "order"
        ));
    }

    @Test
    public void replacesExistingSortWithoutLosingOtherParametersOrFragment() {
        assertEquals(
                "https://www.vinted.pl/catalog?search_text=Samsung&order=newest_first&catalog%5B%5D=3661#top",
                VintedCatalogUrlParameters.withOrReplaced(
                        "https://www.vinted.pl/catalog?search_text=Samsung&order=relevance&catalog%5B%5D=3661#top",
                        "order", "newest_first"
                )
        );
    }

    @Test
    public void appendsSortWhenAbsentWithOrWithoutQuery() {
        assertEquals(
                "https://www.vinted.pl/catalog?order=newest_first",
                VintedCatalogUrlParameters.withOrReplaced(
                        "https://www.vinted.pl/catalog", "order", "newest_first"
                )
        );
        assertEquals(
                "https://www.vinted.pl/catalog?search_text=Samsung&order=newest_first#panel",
                VintedCatalogUrlParameters.withOrReplaced(
                        "https://www.vinted.pl/catalog?search_text=Samsung#panel",
                        "order", "newest_first"
                )
        );
    }

    @Test
    public void preservesExistingPriceAndAllOtherParametersWhenReplacingPrice() {
        assertEquals(
                "https://www.vinted.pl/catalog?price_from=850&price_to=1400&order=newest_first",
                VintedCatalogUrlParameters.withOrReplaced(
                        "https://www.vinted.pl/catalog?price_from=100&price_to=1400&order=newest_first",
                        "price_from", "850"
                )
        );
    }
}
