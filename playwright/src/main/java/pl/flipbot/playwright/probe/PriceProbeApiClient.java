package pl.flipbot.playwright.probe;

import pl.flipbot.playwright.api.ApiClient;
import pl.flipbot.playwright.exception.ApiException;

import java.net.http.HttpResponse;
import java.util.Optional;

public class PriceProbeApiClient extends ApiClient {

    public Optional<PriceProbeAssignmentDto> claimNext(Long botId) {
        HttpResponse<String> response = post(
                "/api/price-probes/bots/" + botId + "/claim"
        );

        if (response.statusCode() == 204) {
            return Optional.empty();
        }

        if (response.statusCode() != 200) {
            throw new ApiException(
                    "Price probe claim failed with HTTP "
                            + response.statusCode()
                            + ": "
                            + response.body()
            );
        }

        return Optional.of(
                readBody(response, PriceProbeAssignmentDto.class)
        );
    }

    public void complete(
            Long botId,
            Long probeId,
            PriceProbeOutcomeDto outcome
    ) {
        HttpResponse<String> response = patch(
                "/api/price-probes/bots/"
                        + botId
                        + "/"
                        + probeId,
                outcome
        );

        if (response.statusCode() != 200) {
            throw new ApiException(
                    "Price probe completion failed with HTTP "
                            + response.statusCode()
                            + ": "
                            + response.body()
            );
        }
    }
}
