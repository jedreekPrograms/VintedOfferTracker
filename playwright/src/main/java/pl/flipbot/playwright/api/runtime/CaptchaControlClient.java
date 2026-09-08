package pl.flipbot.playwright.api.runtime;

import pl.flipbot.playwright.api.ApiClient;
import pl.flipbot.playwright.exception.ApiException;

import java.net.http.HttpResponse;

public class CaptchaControlClient extends ApiClient {

    public CaptchaControlStateResponse getState(Long botId) {
        HttpResponse<String> response = get(path(botId));
        validate(botId, "read", response);
        return readBody(response, CaptchaControlStateResponse.class);
    }

    public CaptchaControlStateResponse markReady(Long botId) {
        return postState(botId, "/ready", "ready");
    }

    public CaptchaControlStateResponse markCompleted(Long botId) {
        return postState(botId, "/completed", "completed");
    }

    public CaptchaControlStateResponse markFailed(Long botId) {
        return postState(botId, "/failed", "failed");
    }

    private CaptchaControlStateResponse postState(
            Long botId,
            String suffix,
            String operation
    ) {
        HttpResponse<String> response = post(path(botId) + suffix);
        validate(botId, operation, response);
        return readBody(response, CaptchaControlStateResponse.class);
    }

    private String path(Long botId) {
        return "/api/bots/" + botId + "/runtime/captcha-control";
    }

    private void validate(
            Long botId,
            String operation,
            HttpResponse<String> response
    ) {
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return;
        }

        throw new ApiException(
                "CAPTCHA control "
                        + operation
                        + " failed for bot "
                        + botId
                        + ". HTTP "
                        + response.statusCode()
                        + ": "
                        + response.body()
        );
    }
}
