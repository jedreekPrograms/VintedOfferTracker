package pl.flipbot.playwright.api.listing.dto;

import java.util.List;

public record DiscoverListingsRequestDto(

        List<CreateListingRequestDto> listings

) {
}