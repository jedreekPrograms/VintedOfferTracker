package pl.flipbot.listing.dto;

public record ActionRequiredListingResponse(
        Long botId,
        String botName,
        ListingResponse listing
) {
}
