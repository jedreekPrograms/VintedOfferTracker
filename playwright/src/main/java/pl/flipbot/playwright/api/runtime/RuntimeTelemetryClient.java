package pl.flipbot.playwright.api.runtime;

import pl.flipbot.playwright.api.ApiClient;
import pl.flipbot.playwright.exception.ApiException;

import java.net.http.HttpResponse;

public class RuntimeTelemetryClient extends ApiClient {

    public RuntimeTelemetryStateResponse sendEvent(
            Long botId,
            RuntimeTelemetryEventRequest request
    ) {
        HttpResponse<String> response = patch(
                "/api/bots/" + botId + "/runtime",
                request
        );

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ApiException(
                    "Runtime telemetry update failed for bot "
                            + botId
                            + ". HTTP "
                            + response.statusCode()
                            + ": "
                            + response.body()
            );
        }

        return readBody(response, RuntimeTelemetryStateResponse.class);
    }
}
