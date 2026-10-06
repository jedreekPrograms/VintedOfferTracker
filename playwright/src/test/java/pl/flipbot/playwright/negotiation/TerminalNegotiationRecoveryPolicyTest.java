package pl.flipbot.playwright.negotiation;

import org.junit.Test;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.model.BotConfigurationDto;
import pl.flipbot.playwright.model.NegotiationStepDto;
import pl.flipbot.playwright.model.NegotiationStrategySnapshotDto;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class TerminalNegotiationRecoveryPolicyTest {

    private final TerminalNegotiationRecoveryPolicy policy =
            new TerminalNegotiationRecoveryPolicy();

    @Test
    public void terminalSellerCounterBelowCapBecomesActionRequired() {
        TerminalNegotiationRecoveryDecision recovery = policy.decide(
                terminalListing(3, "1230.00", "REJECTED"),
                NegotiationConversationSnapshot.sellerCounterOffer(
                        new BigDecimal("1275.00")
                ),
                adaptiveConfiguration("1450.00")
        );

        assertEquals(
                TerminalNegotiationRecoveryDecision.Action.MARK_ACTION_REQUIRED,
                recovery.action()
        );
        assertNotNull(recovery.negotiationDecision());
        assertEquals(
                NegotiationDecisionType.MARK_ACTION_REQUIRED,
                recovery.negotiationDecision().type()
        );
        assertEquals(
                0,
                new BigDecimal("1275.00").compareTo(
                        recovery.negotiationDecision()
                                .sellerCounterOfferPrice()
                )
        );
    }

    @Test
    public void nonFinalRejectedConversationReopensInsteadOfBeingLost() {
        TerminalNegotiationRecoveryDecision recovery = policy.decide(
                terminalListing(2, "1100.00", "REJECTED"),
                NegotiationConversationSnapshot.rejected("Odrzucone"),
                adaptiveConfiguration("1450.00")
        );

        assertEquals(
                TerminalNegotiationRecoveryDecision.Action.REOPEN_NEGOTIATING,
                recovery.action()
        );
    }

    @Test
    public void nonFinalPendingConversationReopensForNormalTimeoutPolicy() {
        TerminalNegotiationRecoveryDecision recovery = policy.decide(
                terminalListing(1, "1000.00", "EXPIRED"),
                NegotiationConversationSnapshot.pending("Oczekujące"),
                adaptiveConfiguration("1450.00")
        );

        assertEquals(
                TerminalNegotiationRecoveryDecision.Action.REOPEN_NEGOTIATING,
                recovery.action()
        );
        assertEquals(true, recovery.awaitingSellerResponse());
    }

    @Test
    public void legacyNonFinalRejectedWithoutPinnedStrategyDoesNotAutoReopen() {
        TerminalNegotiationRecoveryDecision recovery = policy.decide(
                terminalListingWithoutSnapshot(2, "1100.00", "REJECTED"),
                NegotiationConversationSnapshot.rejected("Odrzucone"),
                adaptiveConfiguration("1450.00")
        );

        assertEquals(
                TerminalNegotiationRecoveryDecision.Action.KEEP_TERMINAL,
                recovery.action()
        );
    }

    @Test
    public void finalStepHighCounterStaysTerminal() {
        TerminalNegotiationRecoveryDecision recovery = policy.decide(
                terminalListing(5, "1450.00", "REJECTED"),
                NegotiationConversationSnapshot.sellerCounterOffer(
                        new BigDecimal("1600.00")
                ),
                adaptiveConfiguration("1450.00")
        );

        assertEquals(
                TerminalNegotiationRecoveryDecision.Action.KEEP_TERMINAL,
                recovery.action()
        );
    }

    private BotConfigurationDto adaptiveConfiguration(String cap) {
        BotConfigurationDto configuration = new BotConfigurationDto();
        configuration.setAutoRaiseOfferToVintedMinimum(true);
        configuration.setMaxAutomaticOffer(new BigDecimal(cap));
        configuration.setNegotiationSteps(
                List.of(
                        step(1, "900", "950"),
                        step(2, "1000", "1050"),
                        step(3, "1100", "1150"),
                        step(4, "1200", "1250"),
                        step(5, "1300", "1350")
                )
        );
        return configuration;
    }

    private NegotiationStepDto step(
            int number,
            String offer,
            String accepted
    ) {
        NegotiationStepDto step = new NegotiationStepDto();
        step.setStepNumber(number);
        step.setOfferPrice(new BigDecimal(offer));
        step.setMaxAcceptedCounterOffer(new BigDecimal(accepted));
        step.setMessage("message " + number);
        return step;
    }

    private ListingResponseDto terminalListing(
            int currentStep,
            String currentPrice,
            String status
    ) {
        NegotiationStrategySnapshotDto snapshot =
                new NegotiationStrategySnapshotDto();
        snapshot.setVersion(1);
        snapshot.setNegotiationSteps(List.of());

        return new ListingResponseDto(
                100L,
                "9700000000",
                "Samsung Galaxy S25",
                "https://www.vinted.pl/items/9700000000-samsung-galaxy-s25",
                new BigDecimal("2000.00"),
                new BigDecimal(currentPrice),
                currentStep,
                false,
                "12345",
                "https://www.vinted.pl/inbox/12345",
                status,
                "2026-10-05T20:00:00",
                "2026-10-05T18:00:00",
                null,
                null,
                null,
                null,
                null,
                "Samsung / Galaxy S25",
                1,
                snapshot
        );
    }

    private ListingResponseDto terminalListingWithoutSnapshot(
            int currentStep,
            String currentPrice,
            String status
    ) {
        return new ListingResponseDto(
                100L,
                "9700000000",
                "Samsung Galaxy S25",
                "https://www.vinted.pl/items/9700000000-samsung-galaxy-s25",
                new BigDecimal("2000.00"),
                new BigDecimal(currentPrice),
                currentStep,
                false,
                "12345",
                "https://www.vinted.pl/inbox/12345",
                status,
                "2026-10-05T20:00:00"
        );
    }
}
