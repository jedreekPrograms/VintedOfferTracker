package pl.flipbot.playwright.negotiation;

import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.model.BotConfigurationDto;
import pl.flipbot.playwright.model.NegotiationStepDto;

import java.util.Objects;

/**
 * Conservative recovery policy for a recently terminal local row.
 *
 * It never sends an offer directly. A live Vinted conversation must first be
 * inspected. A newly accepted / affordable formal seller offer can be surfaced
 * immediately; otherwise a non-final conversation may only be reopened to
 * NEGOTIATING, after which the normal timers, quota and action guards decide
 * whether another buyer step is actually due.
 */
public class TerminalNegotiationRecoveryPolicy {

    private final NegotiationDecisionService decisionService;

    public TerminalNegotiationRecoveryPolicy() {
        this(new NegotiationDecisionService());
    }

    TerminalNegotiationRecoveryPolicy(
            NegotiationDecisionService decisionService
    ) {
        this.decisionService = Objects.requireNonNull(decisionService);
    }

    public TerminalNegotiationRecoveryDecision decide(
            ListingResponseDto listing,
            NegotiationConversationSnapshot snapshot,
            BotConfigurationDto configuration
    ) {
        Objects.requireNonNull(listing, "Listing cannot be null");
        Objects.requireNonNull(snapshot, "Snapshot cannot be null");
        Objects.requireNonNull(configuration, "Configuration cannot be null");

        return switch (snapshot.result()) {
            case ACCEPTED -> TerminalNegotiationRecoveryDecision.actionRequired(
                    NegotiationDecision.actionRequiredAfterAcceptance()
            );

            case SELLER_COUNTER_OFFER, REJECTED -> {
                NegotiationDecision decision = decisionService.decide(
                        listing,
                        snapshot,
                        configuration
                );

                if (decision.type()
                        == NegotiationDecisionType.MARK_ACTION_REQUIRED) {
                    yield TerminalNegotiationRecoveryDecision.actionRequired(
                            decision
                    );
                }

                if ((decision.type() == NegotiationDecisionType.WAIT
                        || decision.type()
                        == NegotiationDecisionType.SEND_NEXT_STEP)
                        && hasConfiguredNextStep(listing, configuration)) {
                    yield TerminalNegotiationRecoveryDecision.reopen(
                            false,
                            "Live Vinted state is "
                                    + snapshot.result()
                                    + " at non-final step "
                                    + listing.currentStep()
                                    + "; normal negotiation policy still has a later configured step. "
                                    + "Reopen locally without sending anything yet. Decision reason: "
                                    + decision.reason()
                    );
                }

                yield TerminalNegotiationRecoveryDecision.keep(
                        "Live Vinted state is "
                                + snapshot.result()
                                + ", but the normal decision layer has no safe later automated step. "
                                + "Terminal state is kept. Decision: "
                                + decision.type()
                                + ". Reason: "
                                + decision.reason()
                );
            }

            case PENDING -> hasConfiguredNextStep(listing, configuration)
                    ? TerminalNegotiationRecoveryDecision.reopen(
                            true,
                            "Vinted still shows the latest own offer as PENDING and the local row is terminal even though another configured step exists. "
                                    + "Reopen the conversation so the standard no-response timer can continue the ladder safely."
                    )
                    : TerminalNegotiationRecoveryDecision.keep(
                            "Vinted still shows a final-step offer as PENDING. No later configured step exists, so the terminal row is not reopened."
                    );

            case CANCELLED -> TerminalNegotiationRecoveryDecision.keep(
                    "Vinted reports the latest own offer as CANCELLED."
            );

            case UNKNOWN -> TerminalNegotiationRecoveryDecision.keep(
                    "The terminal conversation state could not be recognized safely."
            );
        };
    }

    private boolean hasConfiguredNextStep(
            ListingResponseDto listing,
            BotConfigurationDto configuration
    ) {
        if (listing.currentStep() == null
                || configuration.getNegotiationSteps() == null) {
            return false;
        }

        return configuration.getNegotiationSteps()
                .stream()
                .filter(Objects::nonNull)
                .map(NegotiationStepDto::getStepNumber)
                .filter(Objects::nonNull)
                .anyMatch(stepNumber ->
                        stepNumber > listing.currentStep()
                );
    }
}
