package pl.flipbot.command.dto;

import pl.flipbot.command.BotCommandType;

public record BotCommandWorkerResponse(
        Long id,
        Long botId,
        Long listingId,
        BotCommandType type,
        String conversationUrl
) {
}