package pl.flipbot.playwright.api;

import com.fasterxml.jackson.core.type.TypeReference;
import pl.flipbot.playwright.exception.ApiException;
import pl.flipbot.playwright.model.BotDetailsDto;
import pl.flipbot.playwright.model.RunningBotDto;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.List;

public class BotApiClient extends ApiClient {

    public List<RunningBotDto> getRunningBots() {

        HttpResponse<String> response = get("/api/bots/running");

        try {

            return objectMapper.readValue(
                    response.body(),
                    new TypeReference<List<RunningBotDto>>() {
                    }
            );

        } catch (IOException e) {

            throw new ApiException(
                    "Cannot parse running bots.",
                    e
            );

        }

    }

    public BotDetailsDto getBot(Long botId) {

        HttpResponse<String> response = get("/api/bots/" + botId + "/playwright");

        return readBody(
                response,
                BotDetailsDto.class
        );

    }

}