package pl.flipbot.negotiation;

import org.springframework.stereotype.Component;
import pl.flipbot.listing.Listing;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Component
public class NegotiationEngine {

    public NegotiationDecision processNegotiationResult(
            Listing listing,
            NegotiationResult result,
            BigDecimal counterOffer
    ) {

        return switch (result) {

            case ACCEPTED -> handleAccepted();

            case COUNTER_OFFER -> handleCounterOffer(listing, counterOffer);

            case REJECTED -> handleRejected(listing);

        };

    }

    private NegotiationDecision handleAccepted() {

        return NegotiationDecision.builder()
                .action(NegotiationAction.ACTION_REQUIRED)
                .build();

    }

    private NegotiationDecision handleCounterOffer(
            Listing listing,
            BigDecimal counterOffer
    ) {

        NegotiationStep step = getCurrentNegotiationStep(listing);

        if (counterOffer.compareTo(step.getMaxAcceptedCounterOffer()) <= 0) {

            return NegotiationDecision.builder()
                    .action(NegotiationAction.ACTION_REQUIRED)
                    .build();
        }

        return nextStepOrFinish(listing);

    }

    private NegotiationDecision handleRejected(
            Listing listing
    ) {

        return nextStepOrFinish(listing);
    }

    private NegotiationDecision nextStepOrFinish(
            Listing listing
    ) {

        int nextStep = listing.getCurrentStep() + 1;

        List<NegotiationStep> steps = orderedSteps(listing);

        if (nextStep > steps.size()) {

            return NegotiationDecision.builder()
                    .action(NegotiationAction.FINISH_NEGOTIATION)
                    .build();

        }

        NegotiationStep step = steps.get(nextStep - 1);

        return NegotiationDecision.builder()
                .action(NegotiationAction.SEND_NEXT_OFFER)
                .nextStep(nextStep)
                .offerPrice(step.getOfferPrice())
                .message(step.getMessage())
                .build();
    }

    private NegotiationStep getCurrentNegotiationStep(
            Listing listing
    ) {
        if (listing.getCurrentStep() == null || listing.getCurrentStep() < 1) {
            throw new IllegalStateException(
                    "Listing has no valid current negotiation step"
            );
        }

        List<NegotiationStep> steps = orderedSteps(listing);
        int index = listing.getCurrentStep() - 1;

        if (index >= steps.size()) {
            throw new IllegalStateException(
                    "Listing current step exceeds its product negotiation ladder"
            );
        }

        return steps.get(index);
    }

    private List<NegotiationStep> orderedSteps(Listing listing) {
        List<NegotiationStep> steps;

        if (listing.getAdditionalTarget() != null) {
            steps = listing.getAdditionalTarget().getNegotiationSteps();
        } else if (listing.getBot() != null
                && listing.getBot().getConfiguration() != null) {
            steps = listing.getBot().getConfiguration().getNegotiationSteps();
        } else {
            throw new IllegalStateException(
                    "Listing has no product negotiation configuration"
            );
        }

        if (steps == null || steps.isEmpty()) {
            throw new IllegalStateException(
                    "Listing product has no negotiation steps"
            );
        }

        return steps.stream()
                .sorted(Comparator.comparing(
                        NegotiationStep::getStepNumber,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ))
                .toList();
    }
}
