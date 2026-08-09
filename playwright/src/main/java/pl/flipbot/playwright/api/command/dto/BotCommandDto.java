package pl.flipbot.playwright.api.command.dto;

public record BotCommandDto(
        Long id,
        Long botId,
        Long listingId,
        BotCommandTypeDto type,
        String conversationUrl
) {
}