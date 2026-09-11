package pl.flipbot.listing.dto;

import pl.flipbot.listing.ListingHistoryOutcome;

public record UpdateHistoryOutcomeRequest(
        ListingHistoryOutcome outcome
) {
}
