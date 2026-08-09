package pl.flipbot.playwright.api.command;

import pl.flipbot.playwright.api.ApiClient;
import pl.flipbot.playwright.api.command.dto.BotCommandDto;
import pl.flipbot.playwright.api.command.dto.FailBotCommandRequestDto;

import java.net.http.HttpResponse;

public class BotCommandClient
        extends ApiClient {

    public BotCommandDto claimNextCommand(
            Long botId
    ) {

        HttpResponse<String> response =
                post(
                        "/api/bots/"
                                + botId
                                + "/commands/claim-next"
                );

        if (response.statusCode() == 204) {
            return null;
        }

        if (response.statusCode() != 200) {

            throw new IllegalStateException(
                    "Cannot claim next command for bot "
                            + botId
                            + ". HTTP status: "
                            + response.statusCode()
            );
        }

        return readBody(
                response,
                BotCommandDto.class
        );
    }

    public void completeCommand(
            Long botId,
            Long commandId
    ) {

        HttpResponse<String> response =
                patch(
                        "/api/bots/"
                                + botId
                                + "/commands/"
                                + commandId
                                + "/complete"
                );

        if (response.statusCode() != 204) {

            throw new IllegalStateException(
                    "Cannot complete command "
                            + commandId
                            + " for bot "
                            + botId
                            + ". HTTP status: "
                            + response.statusCode()
            );
        }
    }

    public void failCommand(
            Long botId,
            Long commandId,
            String errorMessage
    ) {

        FailBotCommandRequestDto request =
                new FailBotCommandRequestDto(
                        errorMessage
                );

        HttpResponse<String> response =
                patch(
                        "/api/bots/"
                                + botId
                                + "/commands/"
                                + commandId
                                + "/fail",
                        request
                );

        if (response.statusCode() != 204) {

            throw new IllegalStateException(
                    "Cannot fail command "
                            + commandId
                            + " for bot "
                            + botId
                            + ". HTTP status: "
                            + response.statusCode()
            );
        }
    }
}