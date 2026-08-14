package pl.flipbot.dictionary.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import pl.flipbot.bot.configuration.TargetMode;

@Getter
@Setter
public class CreateDictionaryModelRequest {

    @NotBlank(
            message = "Model name cannot be blank"
    )
    @Size(
            max = 255,
            message = "Model name cannot be longer than 255 characters"
    )
    private String name;

    private TargetMode targetMode;

    private Long categoryId;
}
