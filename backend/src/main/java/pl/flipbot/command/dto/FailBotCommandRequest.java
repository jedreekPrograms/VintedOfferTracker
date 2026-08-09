package pl.flipbot.command.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FailBotCommandRequest(

        @NotBlank
        @Size(max = 1000)
        String errorMessage

) {
}