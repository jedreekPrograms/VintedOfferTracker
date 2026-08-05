package pl.flipbot.dictionary.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateDictionaryBrandRequest {

    @NotBlank(
            message = "Brand name cannot be blank"
    )
    @Size(
            max = 255,
            message = "Brand name cannot be longer than 255 characters"
    )
    private String name;
}
