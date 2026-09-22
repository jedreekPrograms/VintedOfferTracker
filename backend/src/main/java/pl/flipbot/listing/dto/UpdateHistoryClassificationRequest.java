package pl.flipbot.listing.dto;

import jakarta.validation.constraints.NotNull;
import pl.flipbot.listing.HistoryOutcome;
import pl.flipbot.listing.MissedOpportunityReason;
import pl.flipbot.listing.OfferAssessment;

public record UpdateHistoryClassificationRequest(
        @NotNull HistoryOutcome historyOutcome,
        @NotNull OfferAssessment offerAssessment,
        MissedOpportunityReason missedOpportunityReason
) {
}
