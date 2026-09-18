package pl.flipbot.playwright.negotiation;

import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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

    @Test
    public void rejectsCounterofferAboveCapturedOriginalPrice() {
        assertFalse(
                NegotiationConversationProcessor.isPlausibleSellerCounterOffer(
                        new BigDecimal("1091.18"),
                        new BigDecimal("2346.19")
                )
        );
    }

    @Test
    public void acceptsCounterofferAtOrBelowCapturedOriginalPrice() {
        assertTrue(
                NegotiationConversationProcessor.isPlausibleSellerCounterOffer(
                        new BigDecimal("1192.76"),
                        new BigDecimal("1102.26")
                )
        );
        assertTrue(
                NegotiationConversationProcessor.isPlausibleSellerCounterOffer(
                        new BigDecimal("1192.76"),
                        new BigDecimal("1192.76")
                )
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
