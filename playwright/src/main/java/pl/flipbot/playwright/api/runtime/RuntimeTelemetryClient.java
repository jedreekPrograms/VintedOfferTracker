package pl.flipbot.playwright.api.runtime;

import pl.flipbot.playwright.api.ApiClient;
import pl.flipbot.playwright.exception.ApiException;

import java.net.http.HttpResponse;

public class RuntimeTelemetryClient extends ApiClient {

    public RuntimeTelemetryStateResponse getState(Long botId) {
        HttpResponse<String> response = get(
                "/api/bots/" + botId + "/runtime"
        );
        validateResponse(botId, "read", response);
        return readBody(response, RuntimeTelemetryStateResponse.class);
    }

    public RuntimeTelemetryStateResponse sendEvent(
            Long botId,
            RuntimeTelemetryEventRequest request
    ) {
        HttpResponse<String> response = patch(
                "/api/bots/" + botId + "/runtime",
                request
        );
        validateResponse(botId, request.eventType(), response);
        return readBody(response, RuntimeTelemetryStateResponse.class);
    }

    private void validateResponse(
            Long botId,
            String operation,
            HttpResponse<String> response
    ) {
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return;
        }

        throw new ApiException(
                "Runtime telemetry "
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
