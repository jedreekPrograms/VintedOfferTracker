package pl.flipbot.playwright.negotiation;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NegotiationConversationProcessorStatusTest {

    private final NegotiationConversationProcessor processor =
            new NegotiationConversationProcessor(null);

    @Test
    public void recognizesCurrentAcceptedLabel() {
        assertStatus(
                "Zaakceptowane",
                NegotiationConversationResult.ACCEPTED
        );
    }

    @Test
    public void recognizesAcceptedVerbVariant() {
        assertStatus(
                "Zaakceptowano",
                NegotiationConversationResult.ACCEPTED
        );
    }

    @Test
    public void recognizesAcceptedAdjectiveVariant() {
        assertStatus(
                "Oferta zaakceptowana",
                NegotiationConversationResult.ACCEPTED
        );
    }

    @Test
    public void doesNotTreatExplicitlyNotAcceptedLabelAsAccepted() {
        assertStatus(
                "Niezaakceptowane",
                NegotiationConversationResult.UNKNOWN
        );
    }

    @Test
    public void recognizesPendingAndRejectedVariants() {
        assertStatus(
                "Oczekujące",
                NegotiationConversationResult.PENDING
        );

        assertStatus(
                "Odrzucono",
                NegotiationConversationResult.REJECTED
        );
    }

    private void assertStatus(
            String rawStatus,
            NegotiationConversationResult expected
    ) {
        NegotiationConversationSnapshot snapshot =
                processor.createStatusSnapshot(
                        rawStatus
                );

        assertEquals(
                expected,
                snapshot.result()
        );
    }
}
