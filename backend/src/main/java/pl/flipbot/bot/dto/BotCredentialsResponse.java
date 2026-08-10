package pl.flipbot.bot.dto;

public record BotCredentialsResponse(
        String email,
        String password
) {
}