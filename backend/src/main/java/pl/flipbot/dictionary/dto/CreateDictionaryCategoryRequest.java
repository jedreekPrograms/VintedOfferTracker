package pl.flipbot.dictionary.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CreateDictionaryCategoryRequest {

    @NotEmpty(
            message = "Category path cannot be empty"
    )
    @Size(
            max = 20,
            message = "Category path cannot contain more than 20 elements"
    )
    @Valid
    private List<
                @NotBlank(
                        message = "Category path element cannot be blank"
                )
                @Size(
                        max = 255,
                        message = "Category path element cannot be longer than 255 characters"
                )
                String
                > categoryPath;

}
