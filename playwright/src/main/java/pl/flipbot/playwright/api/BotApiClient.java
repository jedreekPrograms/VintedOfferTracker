package pl.flipbot.playwright.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import pl.flipbot.playwright.exception.ApiException;
import pl.flipbot.playwright.model.BotDetailsDto;
import pl.flipbot.playwright.model.RunningBotDto;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BotApiClient extends ApiClient {

    private static final String RUNNING_BOTS_PATH = "/api/scheduler/bots/running";
    private static final String ALL_BOTS_PATH = "/api/bots";
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

    /**
     * Returns every bot ID, including STOPPED bots. Session retention cleanup
     * must never use only the RUNNING endpoint because stopped accounts are
     * expected to keep their encrypted login state for a later restart.
     */
    public Set<Long> getAllBotIds() {
        HttpResponse<String> response = get(ALL_BOTS_PATH);

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ApiException(
                    "GET "
                            + ALL_BOTS_PATH
                            + " returned HTTP "
                            + response.statusCode()
                            + ". body="
                            + summarizeResponseBody(response.body())
            );
        }

        return parseBotIds(response.body());
    }

    Set<Long> parseBotIds(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);

            if (root == null || !root.isArray()) {
                throw new ApiException(
                        "Cannot parse all bots: response is not a JSON array. body="
                                + summarizeResponseBody(body)
                );
            }

            Set<Long> ids = new HashSet<>();

            for (JsonNode bot : root) {
                JsonNode idNode = bot.get("id");

                if (idNode == null || !idNode.canConvertToLong()) {
                    continue;
                }

                long id = idNode.longValue();
                if (id > 0) {
                    ids.add(id);
                }
            }

            return Set.copyOf(ids);

        } catch (ApiException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new ApiException(
                    "Cannot parse all bot IDs. body="
                            + summarizeResponseBody(body),
                    exception
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
