package pl.flipbot.playwright.negotiation;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ConversationContactAvailabilityDetectorTest {

    @Test
    public void enabledOfferButtonMakesConversationAvailable() {
        assertEquals(
                ConversationContactAssessment.State.AVAILABLE,
                ConversationContactAvailabilityDetector.classify(
                        true,
                        true,
                        false,
                        false,
                        false
                )
        );
    }

    @Test
    public void disabledOfferButtonWithoutComposerIsSuspectedUnavailable() {
        assertEquals(
                ConversationContactAssessment.State.SUSPECTED_UNAVAILABLE,
                ConversationContactAvailabilityDetector.classify(
                        true,
                        false,
                        false,
                        false,
                        false
                )
        );
    }

    @Test
    public void enabledMessageComposerWithoutOfferActionIsNotTreatedAsBlocked() {
        assertEquals(
                ConversationContactAssessment.State.OFFER_ACTION_UNAVAILABLE,
                ConversationContactAvailabilityDetector.classify(
                        false,
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
                        true,
                        true
                )
        );
    }
}
