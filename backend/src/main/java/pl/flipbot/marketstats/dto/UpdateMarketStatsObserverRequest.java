package pl.flipbot.marketstats.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UpdateMarketStatsObserverRequest(
        @NotBlank String name,
        @NotBlank @Email String email,
        String password
) {
}
