package pl.flipbot.playwright.negotiation;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ConversationContactAvailabilityDetectorTest {

    @Test
    public void offerButtonMakesConversationAvailable() {
        assertEquals(
                ConversationContactAssessment.State.AVAILABLE,
                ConversationContactAvailabilityDetector.classify(
                        true,
                        false,
                        false,
                        false
                )
        );
    }

    @Test
    public void enabledMessageComposerMakesConversationAvailable() {
        assertEquals(
                ConversationContactAssessment.State.AVAILABLE,
                ConversationContactAvailabilityDetector.classify(
                        false,
                        true,
                        true,
                        false
                )
        );
    }

    @Test
    public void missingOfferAndComposerIsOnlySuspectedAtFirst() {
        assertEquals(
                ConversationContactAssessment.State.SUSPECTED_UNAVAILABLE,
                ConversationContactAvailabilityDetector.classify(
                        false,
                        false,
                        false,
                        false
                )
        );
    }

    @Test
    public void disabledComposerWithoutOfferIsSuspectedUnavailable() {
        assertEquals(
                ConversationContactAssessment.State.SUSPECTED_UNAVAILABLE,
                ConversationContactAvailabilityDetector.classify(
                        false,
                        true,
                        false,
                        false
                )
        );
    }

    @Test
    public void explicitUnavailableSignalWinsEvenIfControlsLookPresent() {
        assertEquals(
                ConversationContactAssessment.State.CONFIRMED_UNAVAILABLE,
                ConversationContactAvailabilityDetector.classify(
                        true,
                        true,
                        true,
                        true
                )
        );
    }
}
