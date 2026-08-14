package pl.flipbot.playwright.api.audit;

import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.ApiClient;
import pl.flipbot.playwright.api.audit.dto.RealActionAuditRequestDto;

import java.net.http.HttpResponse;
import java.util.Objects;

@Slf4j
public class RealActionAuditClient extends ApiClient {

    public void record(
            Long botId,
            Long backendListingId,
            RealActionAuditRequestDto request
    ) {
        Objects.requireNonNull(botId, "Bot id cannot be null");
        Objects.requireNonNull(backendListingId, "Backend listing id cannot be null");
        Objects.requireNonNull(request, "Audit request cannot be null");

        String path =
                "/api/bots/"
                        + botId
                        + "/listings/"
                        + backendListingId
                        + "/real-action-audit";

        HttpResponse<String> response = post(path, request);

        int statusCode = response.statusCode();
        if (statusCode < 200 || statusCode >= 300) {
            throw new IllegalStateException(
                    "Backend could not persist real-action audit. HTTP status: "
                            + statusCode
                            + ", response: "
                            + response.body()
            );
        }

        log.info(
                "[REAL ACTION AUDIT API] Persisted bot={}, listing={}, requestId={}, action={}, step={}, outcome={}, messageStatus={}",
                botId,
                backendListingId,
                request.requestId(),
                request.actionType(),
                request.stepNumber(),
                request.outcome(),
                request.messageStatus()
        );
    }
}
