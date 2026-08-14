package pl.flipbot.dictionary.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateDictionaryModelCategoryRequest {

    @NotNull(message = "Category id is required")
    @Positive(message = "Category id must be positive")
    private Long categoryId;
}
