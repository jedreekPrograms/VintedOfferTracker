package pl.flipbot.playwright.api;

import com.fasterxml.jackson.core.type.TypeReference;
import pl.flipbot.playwright.model.BotDto;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

public class BotApiClient extends ApiClient {

    public List<BotDto> getRunningBots() throws IOException, InterruptedException {

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(
                                URI.create(
                                        baseUrl + "/api/bots/running"
                                )
                        )
                        .GET()
                        .build();

        HttpResponse<String> response =
                httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

        return  objectMapper.readValue(
                response.body(),
                new TypeReference<>() {
                }
        );
    }
}
