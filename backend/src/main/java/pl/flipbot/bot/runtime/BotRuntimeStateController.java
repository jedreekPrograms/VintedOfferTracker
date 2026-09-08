package pl.flipbot.bot.runtime;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pl.flipbot.bot.runtime.captcha.CaptchaControlFailureRequest;
import pl.flipbot.bot.runtime.captcha.CaptchaControlService;
import pl.flipbot.bot.runtime.captcha.CaptchaControlStateResponse;
import pl.flipbot.bot.runtime.dto.BotRuntimeEventRequest;
import pl.flipbot.bot.runtime.dto.BotRuntimeStateResponse;

@RestController
@RequestMapping("/api/bots/{botId}/runtime")
@RequiredArgsConstructor
public class BotRuntimeStateController {

    private final BotRuntimeStateService runtimeStateService;
    private final CaptchaControlService captchaControlService;

    @GetMapping
    public BotRuntimeStateResponse getRuntimeState(
            @PathVariable Long botId
    ) {
        return runtimeStateService.getRuntimeState(botId);
    }

    @PatchMapping
    public BotRuntimeStateResponse applyRuntimeEvent(
            @PathVariable Long botId,
            @RequestBody BotRuntimeEventRequest request
    ) {
        return runtimeStateService.applyEvent(botId, request);
    }

    @PostMapping("/captcha-recovery")
    public BotRuntimeStateResponse requestCaptchaRecovery(
            @PathVariable Long botId
    ) {
        BotRuntimeStateResponse response =
                runtimeStateService.requestCaptchaRecovery(botId);
        captchaControlService.prepare(botId);
        return response;
    }

    @GetMapping("/captcha-control")
    public CaptchaControlStateResponse getCaptchaControlState(
            @PathVariable Long botId
    ) {
        return captchaControlService.getState(botId);
    }

    @PostMapping("/captcha-control/ready")
    public CaptchaControlStateResponse markCaptchaControlReady(
            @PathVariable Long botId
    ) {
        return captchaControlService.markReady(botId);
    }

    @PostMapping("/captcha-control/hold/start")
    public CaptchaControlStateResponse startCaptchaHold(
            @PathVariable Long botId
    ) {
        return captchaControlService.startHold(botId);
    }

    @PostMapping("/captcha-control/hold/heartbeat")
    public CaptchaControlStateResponse heartbeatCaptchaHold(
            @PathVariable Long botId
    ) {
        return captchaControlService.heartbeat(botId);
    }

    @PostMapping("/captcha-control/hold/end")
    public CaptchaControlStateResponse endCaptchaHold(
            @PathVariable Long botId
    ) {
        return captchaControlService.endHold(botId);
    }

    @PostMapping("/captcha-control/completed")
    public CaptchaControlStateResponse markCaptchaControlCompleted(
            @PathVariable Long botId
    ) {
        return captchaControlService.markCompleted(botId);
    }

    @PostMapping("/captcha-control/failed")
    public CaptchaControlStateResponse markCaptchaControlFailed(
            @PathVariable Long botId,
            @RequestBody(required = false) CaptchaControlFailureRequest request
    ) {
        return captchaControlService.markFailed(
                botId,
                request == null ? null : request.message()
        );
    }
}
