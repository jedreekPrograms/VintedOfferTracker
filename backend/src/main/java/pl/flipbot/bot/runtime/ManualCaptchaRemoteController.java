package pl.flipbot.bot.runtime;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bots/{botId}/runtime/captcha-remote")
@RequiredArgsConstructor
public class ManualCaptchaRemoteController {

    private final ManualCaptchaRemoteProxyService proxyService;

    @GetMapping("/state")
    public ResponseEntity<byte[]> state(@PathVariable Long botId) {
        return proxyService.getState(botId);
    }

    @GetMapping("/screenshot")
    public ResponseEntity<byte[]> screenshot(@PathVariable Long botId) {
        return proxyService.getScreenshot(botId);
    }

    @PostMapping("/pointer")
    public ResponseEntity<byte[]> pointer(
            @PathVariable Long botId,
            @RequestBody byte[] json
    ) {
        return proxyService.sendPointer(botId, json);
    }
}
