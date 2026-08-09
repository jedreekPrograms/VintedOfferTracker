package pl.flipbot.command;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pl.flipbot.command.dto.BotCommandResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import pl.flipbot.command.dto.BotCommandWorkerResponse;
import pl.flipbot.command.dto.FailBotCommandRequest;

@RestController
@RequestMapping("/api/bots/{botId}/commands")
@RequiredArgsConstructor
public class BotCommandController {

    private final BotCommandService botCommandService;

    @PostMapping(
            "/listings/{listingId}/open-conversation"
    )
    public BotCommandResponse openConversation(
            @PathVariable Long botId,
            @PathVariable Long listingId
    ) {

        return botCommandService
                .createOpenConversationCommand(
                        botId,
                        listingId
                );
    }

    @PostMapping("/claim-next")
    public ResponseEntity<BotCommandWorkerResponse> claimNextCommand(
            @PathVariable Long botId
    ) {

        BotCommandWorkerResponse command =
                botCommandService
                        .claimNextCommand(
                                botId
                        );

        if (command == null) {

            return ResponseEntity
                    .noContent()
                    .build();
        }

        return ResponseEntity.ok(
                command
        );
    }

    @PatchMapping("/{commandId}/complete")
    public ResponseEntity<Void> completeCommand(
            @PathVariable Long botId,
            @PathVariable Long commandId
    ) {

        botCommandService.completeCommand(
                botId,
                commandId
        );

        return ResponseEntity
                .noContent()
                .build();
    }

    @PatchMapping("/{commandId}/fail")
    public ResponseEntity<Void> failCommand(
            @PathVariable Long botId,
            @PathVariable Long commandId,
            @Valid
            @RequestBody
            FailBotCommandRequest request
    ) {

        botCommandService.failCommand(
                botId,
                commandId,
                request.errorMessage()
        );

        return ResponseEntity
                .noContent()
                .build();
    }
}