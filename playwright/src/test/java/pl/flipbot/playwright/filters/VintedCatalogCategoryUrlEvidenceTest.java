package pl.flipbot.playwright.filters;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VintedCatalogCategoryUrlEvidenceTest {

    @Test
    public void matchesObservedSingleEncodedMobilePhoneId() {
        assertTrue(VintedCatalogCategoryUrlEvidence.matchesExactlyOneCategory(
                "https://www.vinted.pl/catalog?catalog%5B%5D=3661", "3661"
        ));
    }

    @Test
    public void acceptsLiteralBracketsAndUnrelatedSearchParameters() {
        assertTrue(VintedCatalogCategoryUrlEvidence.matchesExactlyOneCategory(
                "https://www.vinted.pl/catalog?search_text=Samsung&catalog[]=3661&page=2",
                "3661"
        ));
    }

    @Test
    public void rejectsWrongOrAbsentPhoneCategoryId() {
        assertFalse(VintedCatalogCategoryUrlEvidence.matchesExactlyOneCategory(
                "https://www.vinted.pl/catalog?catalog%5B%5D=3662", "3661"
        ));
        assertFalse(VintedCatalogCategoryUrlEvidence.matchesExactlyOneCategory(
                "https://www.vinted.pl/catalog?brand_ids%5B%5D=109048", "3661"
        ));
    }

    @Test
    public void rejectsConflictingDuplicateCategoryParametersInEitherOrder() {
        assertFalse(VintedCatalogCategoryUrlEvidence.matchesExactlyOneCategory(
                "https://www.vinted.pl/catalog?catalog%5B%5D=3661&catalog%5B%5D=3662",
                "3661"
        ));
        assertFalse(VintedCatalogCategoryUrlEvidence.matchesExactlyOneCategory(
                "https://www.vinted.pl/catalog?catalog%5B%5D=3662&catalog%5B%5D=3661",
                "3661"
        ));
    }

    @Test
    public void rejectsRepeatedSameIdRatherThanMistakingItForOneParameter() {
        assertFalse(VintedCatalogCategoryUrlEvidence.matchesExactlyOneCategory(
                "https://www.vinted.pl/catalog?catalog[]=3661&catalog%5B%5D=3661",
                "3661"
        ));
    }

    @Test
    public void rejectsOtherVintedPagesOrLookalikeHosts() {
        assertFalse(VintedCatalogCategoryUrlEvidence.matchesExactlyOneCategory(
                "https://www.vinted.pl/items/123?catalog%5B%5D=3661", "3661"
        ));
        assertFalse(VintedCatalogCategoryUrlEvidence.matchesExactlyOneCategory(
                "https://www.vinted.pl.example.org/catalog?catalog%5B%5D=3661",
                "3661"
        ));
        assertFalse(VintedCatalogCategoryUrlEvidence.matchesExactlyOneCategory(
                "http://www.vinted.pl/catalog?catalog%5B%5D=3661",
                "3661"
        ));
    }

    @Test
    public void rejectsEmptyMalformedOrNonNumericValues() {
        assertFalse(VintedCatalogCategoryUrlEvidence.matchesExactlyOneCategory(
                "https://www.vinted.pl/catalog?catalog%5B%5D=", "3661"
        ));
        assertFalse(VintedCatalogCategoryUrlEvidence.matchesExactlyOneCategory(
                "https://www.vinted.pl/catalog?catalog%5B%5D=%GG", "3661"
        ));
        assertFalse(VintedCatalogCategoryUrlEvidence.matchesExactlyOneCategory(
                "https://www.vinted.pl/catalog?catalog%5B%5D=3661", "phone"
        ));
        assertFalse(VintedCatalogCategoryUrlEvidence.matchesExactlyOneCategory(
                "https://www.vinted.pl/catalog?catalog%5B%5D=3661", null
        ));
    }

    @Test
    public void fragmentDoesNotChangeAcceptedQueryCategory() {
        assertTrue(VintedCatalogCategoryUrlEvidence.matchesExactlyOneCategory(
                "https://www.vinted.pl/catalog?catalog%5B%5D=3661#details", "3661"
        ));
    }
}
