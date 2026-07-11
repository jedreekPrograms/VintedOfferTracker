package pl.flipbot.negotiation;

import org.springframework.stereotype.Component;
import pl.flipbot.listing.Listing;

import java.math.BigDecimal;

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

        int maxSteps = listing.getBot()
                .getConfiguration()
                .getNegotiationSteps()
                .size();

        if (nextStep > maxSteps) {

            return NegotiationDecision.builder()
                    .action(NegotiationAction.FINISH_NEGOTIATION)
                    .build();

        }

        NegotiationStep step = listing.getBot()
                .getConfiguration()
                .getNegotiationSteps()
                .get(nextStep - 1);

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

        return listing.getBot()
                .getConfiguration()
                .getNegotiationSteps()
                .get(listing.getCurrentStep() - 1);
    }
}
