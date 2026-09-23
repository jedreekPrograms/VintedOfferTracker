package pl.flipbot.listing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HistoryLegacyEnumConverterTest {

    private final HistoryOutcomeConverter outcomeConverter =
            new HistoryOutcomeConverter();
    private final OfferAssessmentConverter assessmentConverter =
            new OfferAssessmentConverter();
    private final MissedOpportunityReasonConverter reasonConverter =
            new MissedOpportunityReasonConverter();

    @Test
    void mapsKnownLegacyPurchaseOutcomeWithoutThrowing() {
        assertEquals(
                HistoryOutcome.PURCHASED,
                outcomeConverter.convertToEntityAttribute("PURCHASED_BY_ME")
        );
    }

    @Test
    void unknownLegacyOutcomeFallsBackToUnclassified() {
        assertEquals(
                HistoryOutcome.UNCLASSIFIED,
                outcomeConverter.convertToEntityAttribute("SOME_OLD_VALUE")
        );
    }

    @Test
    void unknownAssessmentFallsBackToUnassessed() {
        assertEquals(
                OfferAssessment.UNASSESSED,
                assessmentConverter.convertToEntityAttribute("OLD_RISK_VALUE")
        );
    }

    @Test
    void unknownMissedReasonFallsBackToNull() {
        assertNull(
                reasonConverter.convertToEntityAttribute("OLD_REASON")
        );
    }
}
