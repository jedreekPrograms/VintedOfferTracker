package pl.flipbot.dictionary;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.flipbot.dictionary.dto.DictionaryModelResponse;
import pl.flipbot.dictionary.dto.UpdateDictionaryModelCategoryRequest;

@RestController
@RequestMapping("/api/dictionaries/brands/{brandId}/models/{modelId}/category")
@RequiredArgsConstructor
public class DictionaryModelCategoryController {

    private final DictionaryModelCategoryService service;

    @PatchMapping
    public ResponseEntity<DictionaryModelResponse> updateCategory(
            @PathVariable Long brandId,
            @PathVariable Long modelId,
            @Valid @RequestBody UpdateDictionaryModelCategoryRequest request
    ) {
        return ResponseEntity.ok(
                service.updateCategory(
                        brandId,
                        modelId,
                        request
                )
        );
    }
}
