package pl.flipbot.playwright.api.guard;

import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.ApiClient;
import pl.flipbot.playwright.api.guard.dto.AcquireRealActionGuardRequestDto;
import pl.flipbot.playwright.api.guard.dto.RealActionGuardResponseDto;
import pl.flipbot.playwright.api.guard.dto.ReleaseRealActionGuardRequestDto;

import java.net.http.HttpResponse;
import java.util.Objects;
import java.util.UUID;

@Slf4j
public class RealActionGuardClient extends ApiClient {

    public RealActionGuardResponseDto acquire(
            Long botId,
            Long backendListingId,
            String actionType,
            Integer stepNumber,
            UUID requestId
    ) {
        Objects.requireNonNull(botId, "Bot id cannot be null");
        Objects.requireNonNull(backendListingId, "Backend listing id cannot be null");
        Objects.requireNonNull(actionType, "Action type cannot be null");
        Objects.requireNonNull(stepNumber, "Step number cannot be null");
        Objects.requireNonNull(requestId, "Request id cannot be null");

        String path =
                "/api/bots/"
                        + botId
                        + "/listings/"
                        + backendListingId
                        + "/real-action-guard/acquire";

        HttpResponse<String> response =
                post(
                        path,
                        new AcquireRealActionGuardRequestDto(
                                requestId,
                                actionType,
                                stepNumber
                        )
                );

        validateResponse(
                response,
                "acquire real action guard for bot "
                        + botId
                        + ", listing "
                        + backendListingId
        );

        RealActionGuardResponseDto result =
                readBody(
                        response,
                        RealActionGuardResponseDto.class
                );

        log.info(
                "[REAL ACTION GUARD API] bot={}, listing={}, action={}, step={}, acquired={}, replayed={}, requestId={}",
                botId,
                backendListingId,
                actionType,
                stepNumber,
                result.acquired(),
                result.replayed(),
                result.requestId()
        );

        return result;
    }

    public void release(
            Long botId,
            Long backendListingId,
            UUID requestId
    ) {
        Objects.requireNonNull(botId, "Bot id cannot be null");
        Objects.requireNonNull(backendListingId, "Backend listing id cannot be null");
        Objects.requireNonNull(requestId, "Request id cannot be null");

        String path =
                "/api/bots/"
                        + botId
                        + "/listings/"
                        + backendListingId
                        + "/real-action-guard/release";

        HttpResponse<String> response =
                post(
                        path,
                        new ReleaseRealActionGuardRequestDto(
                                requestId
                        )
                );

        validateResponse(
                response,
                "release real action guard for bot "
                        + botId
                        + ", listing "
                        + backendListingId
        );

        log.info(
                "[REAL ACTION GUARD API] Released guard for bot={}, listing={}, requestId={}",
                botId,
                backendListingId,
                requestId
        );
    }

    private void validateResponse(
            HttpResponse<String> response,
            String operation
    ) {
        int statusCode = response.statusCode();

        if (statusCode >= 200
                && statusCode < 300) {
            return;
        }

        throw new IllegalStateException(
                "Backend could not "
                        + operation
                        + ". HTTP status: "
                        + statusCode
                        + ", response: "
                        + response.body()
        );
    }
}
