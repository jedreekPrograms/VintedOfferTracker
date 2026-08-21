package pl.flipbot.playwright.api;

import com.fasterxml.jackson.core.type.TypeReference;
import pl.flipbot.playwright.exception.ApiException;
import pl.flipbot.playwright.model.BotDetailsDto;
import pl.flipbot.playwright.model.RunningBotDto;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.List;

public class BotApiClient extends ApiClient {

    private static final String RUNNING_BOTS_PATH = "/api/scheduler/bots/running";
    private static final int MAX_DIAGNOSTIC_BODY_LENGTH = 240;

    public List<RunningBotDto> getRunningBots() {

        HttpResponse<String> response = get(RUNNING_BOTS_PATH);

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ApiException(
                    "GET "
                            + RUNNING_BOTS_PATH
                            + " returned HTTP "
                            + response.statusCode()
                            + ". body="
                            + summarizeResponseBody(response.body())
            );
        }

        try {

            return objectMapper.readValue(
                    response.body(),
                    new TypeReference<List<RunningBotDto>>() {
                    }
            );

        } catch (IOException e) {

            throw new ApiException(
                    "Cannot parse running bots from HTTP "
                            + response.statusCode()
                            + ". body="
                            + summarizeResponseBody(response.body()),
                    e
            );

        }

    }

    static String summarizeResponseBody(String body) {
        if (body == null || body.isBlank()) {
            return "<empty>";
        }

        String singleLine = body
                .replaceAll("\\s+", " ")
                .trim();

        if (singleLine.length() <= MAX_DIAGNOSTIC_BODY_LENGTH) {
            return singleLine;
        }

        return singleLine.substring(0, MAX_DIAGNOSTIC_BODY_LENGTH) + "...";
    }

    public BotDetailsDto getBot(Long botId) {

        HttpResponse<String> response = get("/api/bots/" + botId + "/playwright");

        return readBody(
                response,
                BotDetailsDto.class
        );

    }

}
