package pl.flipbot.listing;

import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.flipbot.bot.dto.BotCredentialsResponse;

@RestController
@RequestMapping(
        "/api/bots/{botId}/listings"
)
@RequiredArgsConstructor
public class ActionRequiredCredentialsController {

    private final ActionRequiredCredentialsService
            actionRequiredCredentialsService;

    @GetMapping(
            "/{listingId}/credentials"
    )
    public ResponseEntity<BotCredentialsResponse>
    getCredentials(
            @PathVariable Long botId,
            @PathVariable Long listingId
    ) {

        BotCredentialsResponse response =
                actionRequiredCredentialsService
                        .getCredentials(
                                botId,
                                listingId
                        );

        return ResponseEntity
                .ok()
                .cacheControl(
                        CacheControl.noStore()
                )
                .body(
                        response
                );
    }
}