package pl.flipbot.playwright.api.quota;

import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.ApiClient;
import pl.flipbot.playwright.api.quota.dto.OfferQuotaReservationResponseDto;

import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Slf4j
public class OfferQuotaClient extends ApiClient {

    public OfferQuotaReservationResponseDto reserveSlot(
            Long botId,
            UUID requestId
    ) {
        Objects.requireNonNull(botId, "Bot id cannot be null");
        Objects.requireNonNull(requestId, "Quota request id cannot be null");

        String path = "/api/bots/" + botId + "/offer-quota/reserve";
        HttpResponse<String> response = post(
                path,
                Map.of("requestId", requestId.toString())
        );

        validateResponse(
                response,
                "reserve daily offer quota slot for bot " + botId
        );

        OfferQuotaReservationResponseDto result = readBody(
                response,
                OfferQuotaReservationResponseDto.class
        );

        log.info(
                "Offer quota reservation for bot {} requestId={}. Reserved: {}, used: {}/{}, remaining: {}",
                botId,
                requestId,
                result.reserved(),
                result.used(),
                result.limit(),
                result.remaining()
        );
        return result;
    }

    public void releaseSlot(
            Long botId,
            UUID requestId
    ) {
        Objects.requireNonNull(botId, "Bot id cannot be null");
        Objects.requireNonNull(requestId, "Quota request id cannot be null");

        String path = "/api/bots/" + botId + "/offer-quota/release";
        HttpResponse<String> response = post(
                path,
                Map.of("requestId", requestId.toString())
        );

        validateResponse(
                response,
                "release daily offer quota slot for bot " + botId
        );

        log.info(
                "Released daily offer quota slot for bot {} requestId={}",
                botId,
                requestId
        );
    }

    private void validateResponse(
            HttpResponse<String> response,
            String operation
    ) {
        int statusCode = response.statusCode();
        if (statusCode >= 200 && statusCode < 300) {
            return;
        }

        throw new IllegalStateException(
                "Backend could not " + operation
                        + ". HTTP status: " + statusCode
                        + ", response: " + response.body()
        );
    }
}
