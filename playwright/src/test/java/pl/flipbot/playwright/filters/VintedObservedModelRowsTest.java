package pl.flipbot.playwright.filters;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Anonymized actual model-picker DOM observation (October 2026).
 * Each row has exactly three test IDs: canonical row, --title and --suffix.
 * The collection IDs are test fixture data, NOT a production model-ID map.
 */
public class VintedObservedModelRowsTest {

    private record ObservedOption(String visibleLabel, String collectionId) { }

    private static final List<ObservedOption> EXPANDED_S25_OPTIONS = List.of(
            new ObservedOption("Galaxy S25", "9141"),
            new ObservedOption("Galaxy S25 Edge", "9976"),
            new ObservedOption("Galaxy S25 FE", "9977"),
            new ObservedOption("Galaxy S25 Ultra", "9142"),
            new ObservedOption("Galaxy S25+", "9143")
    );

    @Test
    public void everyObservedRowAndTitleResolveToTheSameExactCollectionId() {
        for (ObservedOption option : EXPANDED_S25_OPTIONS) {
            String row = "selectable-item-brand_collection-" + option.collectionId();
            String title = row + "--title";
            String suffix = row + "--suffix";

            assertEquals(option.collectionId(),
                    VintedModelOptionIdentity.modelCollectionIdFromTestId(row));
            assertEquals(option.collectionId(),
                    VintedModelOptionIdentity.modelCollectionIdFromTestId(title));
            assertEquals(row,
                    VintedModelOptionIdentity.canonicalModelRowTestId(option.collectionId()));
            assertEquals(suffix,
                    FilterActions.exactModelSuffixTestId(option.collectionId()));

            // Clickable suffix does not itself prove the model identity.
            assertNull(VintedModelOptionIdentity.modelCollectionIdFromTestId(suffix));
        }
    }

    @Test
    public void allFiveObservedS25VariantsRemainMutuallyExclusive() {
        for (ObservedOption selected : EXPANDED_S25_OPTIONS) {
            for (ObservedOption candidate : EXPANDED_S25_OPTIONS) {
                boolean sameModel = selected.collectionId().equals(candidate.collectionId());

                assertEquals(selected.visibleLabel() + " vs " + candidate.visibleLabel(),
                        sameModel,
                        VintedModelOptionIdentity.exactVisibleModelLabelMatches(
                                selected.visibleLabel(), candidate.visibleLabel()));

                if (sameModel) {
                    assertTrue(VintedModelOptionIdentity.exactModelOptionPattern(
                            selected.visibleLabel()).matcher(candidate.visibleLabel()).matches());
                } else {
                    assertFalse(VintedModelOptionIdentity.exactModelOptionPattern(
                            selected.visibleLabel()).matcher(candidate.visibleLabel()).matches());
                }
            }
        }
    }

    @Test
    public void metadataDoesNotTurnAnAdjacentModelIntoTheRequestedOne() {
        assertTrue(VintedModelOptionIdentity.exactVisibleModelLabelMatches(
                "Galaxy S25 FE", "Galaxy S25 FE\n12 przedmiotów"));
        assertFalse(VintedModelOptionIdentity.exactVisibleModelLabelMatches(
                "Galaxy S25", "Galaxy S25 FE\n12 przedmiotów"));
        assertFalse(VintedModelOptionIdentity.exactVisibleModelLabelMatches(
                "Galaxy S25", "Galaxy S25\nFE\n12 przedmiotów"));
    }
}
