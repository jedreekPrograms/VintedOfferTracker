package pl.flipbot.bot.configuration;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.flipbot.bot.dto.BotAdditionalTargetResponse;
import pl.flipbot.bot.dto.UpsertBotAdditionalTargetRequest;

import java.util.List;

@RestController
@RequestMapping("/api/bots/{botId}/additional-targets")
@RequiredArgsConstructor
public class BotAdditionalTargetController {

    private final BotAdditionalTargetService service;

    @GetMapping
    public ResponseEntity<List<BotAdditionalTargetResponse>> list(
            @PathVariable Long botId
    ) {
        return ResponseEntity.ok(service.listActive(botId));
    }

    @PostMapping
    public ResponseEntity<BotAdditionalTargetResponse> create(
            @PathVariable Long botId,
            @Valid @RequestBody UpsertBotAdditionalTargetRequest request
    ) {
        return ResponseEntity.ok(service.create(botId, request));
    }

    @PatchMapping("/{targetId}")
    public ResponseEntity<BotAdditionalTargetResponse> update(
            @PathVariable Long botId,
            @PathVariable Long targetId,
            @Valid @RequestBody UpsertBotAdditionalTargetRequest request
    ) {
        return ResponseEntity.ok(service.update(botId, targetId, request));
    }

    @DeleteMapping("/{targetId}")
    public ResponseEntity<Void> deactivate(
            @PathVariable Long botId,
            @PathVariable Long targetId
    ) {
        service.deactivate(botId, targetId);
        return ResponseEntity.noContent().build();
    }
}
