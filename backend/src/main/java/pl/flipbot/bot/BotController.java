package pl.flipbot.bot;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import pl.flipbot.bot.dto.BotEditCapabilitiesResponse;
import pl.flipbot.bot.dto.BotPlaywrightResponse;
import pl.flipbot.bot.dto.BotResponse;
import pl.flipbot.bot.dto.CreateBotRequest;
import pl.flipbot.bot.dto.RunningBotResponse;
import pl.flipbot.bot.dto.UpdateBotRequest;

import java.net.InetAddress;
import java.util.List;

@RestController
@RequestMapping("/api/bots")
@RequiredArgsConstructor
public class BotController {

    private final BotService botService;

    private final BotDeletionService botDeletionService;

    private final BotSharedNegotiationBudgetGuard sharedBudgetGuard;

    @GetMapping
    public List<BotResponse> getAllBots() {
        return botService.getAllBots();
    }

    @GetMapping("/{botId}")
    public BotResponse getBot(
            @PathVariable Long botId
    ) {
        return botService.getBot(botId);
    }

    @GetMapping("/{botId}/edit-capabilities")
    public BotEditCapabilitiesResponse getEditCapabilities(
            @PathVariable Long botId
    ) {
        return botService.getEditCapabilities(botId);
    }

    @PostMapping
    public BotResponse createBot(
            @Valid @RequestBody CreateBotRequest request
    ) {
        sharedBudgetGuard.validateCreate(request);
        return botService.createBot(request);
    }

    @PatchMapping("/{botId}")
    public BotResponse updateBot(
            @PathVariable Long botId,
            @Valid @RequestBody UpdateBotRequest request
    ) {
        sharedBudgetGuard.validateUpdate(botId, request);
        return botService.updateBot(botId, request);
    }

    @DeleteMapping("/{botId}")
    public void deleteBot(
            @PathVariable Long botId
    ) {
        botDeletionService.deleteBot(botId);
    }

    @GetMapping("/running")
    public List<RunningBotResponse> getRunningBotIds() {
        return botService.getRunningBotIds();
    }

    @PatchMapping("/{botId}/start")
    public void startBot(
            @PathVariable Long botId
    ) {
        botService.startBot(botId);
    }

    @PatchMapping("/{botId}/stop")
    public void stopBot(
            @PathVariable Long botId
    ) {
        botService.stopBot(botId);
    }

    @GetMapping("/{botId}/playwright")
    public ResponseEntity<BotPlaywrightResponse> getPlaywrightBot(
            @PathVariable Long botId,
            HttpServletRequest request
    ) {
        if (!isLoopbackAddress(request.getRemoteAddr())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Playwright credential endpoint is available only from the local machine."
            );
        }

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(botService.getPlaywrightBot(botId));
    }

    static boolean isLoopbackAddress(String rawAddress) {
        if (rawAddress == null || rawAddress.isBlank()) {
            return false;
        }

        try {
            return InetAddress.getByName(rawAddress).isLoopbackAddress();
        } catch (Exception exception) {
            return false;
        }
    }
}
