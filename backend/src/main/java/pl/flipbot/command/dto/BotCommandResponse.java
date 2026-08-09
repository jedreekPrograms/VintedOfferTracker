package pl.flipbot.command.dto;

import pl.flipbot.command.BotCommandStatus;
import pl.flipbot.command.BotCommandType;

import java.time.Instant;

public record BotCommandResponse(
        Long id,
        Long botId,
        Long listingId,
        BotCommandType type,
        BotCommandStatus status,
        Instant createdAt
) {
}