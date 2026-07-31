package pl.flipbot.listing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class DiscoverListingsRequest {

    @NotEmpty
    @Size(max = 500)
    private List<@Valid CreateListingRequest> listings;

}