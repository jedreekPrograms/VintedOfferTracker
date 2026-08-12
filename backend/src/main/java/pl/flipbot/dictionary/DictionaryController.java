package pl.flipbot.dictionary;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.flipbot.dictionary.dto.*;

import java.util.List;

@RestController
@RequestMapping(
        "/api/dictionaries"
)
@RequiredArgsConstructor
public class DictionaryController {

    private final DictionaryBrandService dictionaryBrandService;

    private final DictionaryModelService dictionaryModelService;

    private final DictionaryCategoryService dictionaryCategoryService;

    private final DictionaryMutationService dictionaryMutationService;


    @GetMapping("/categories")
    public ResponseEntity<List<DictionaryCategoryResponse>>
    getAllCategories() {

        return ResponseEntity.ok(
                dictionaryCategoryService.getAllCategories()
        );
    }


    @PostMapping("/categories")
    public ResponseEntity<DictionaryCategoryResponse>
    createCategory(
            @Valid
            @RequestBody
            CreateDictionaryCategoryRequest request
    ) {

        DictionaryCategoryResponse createdCategory =
                dictionaryCategoryService.createCategory(
                        request
                );

        return ResponseEntity
                .status(
                        HttpStatus.CREATED
                )
                .body(
                        createdCategory
                );
    }


    @PatchMapping("/categories/{categoryId}")
    public ResponseEntity<DictionaryCategoryResponse>
    updateCategory(
            @PathVariable Long categoryId,
            @Valid
            @RequestBody
            CreateDictionaryCategoryRequest request
    ) {

        return ResponseEntity.ok(
                dictionaryMutationService.updateCategory(
                        categoryId,
                        request
                )
        );
    }


    @DeleteMapping("/categories/{categoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(
            @PathVariable Long categoryId
    ) {

        dictionaryMutationService.deleteCategory(
                categoryId
        );
    }


    @GetMapping("/brands")
    public ResponseEntity<List<DictionaryBrandResponse>>
    getAllBrands() {

        return ResponseEntity.ok(
                dictionaryBrandService.getAllBrands()
        );
    }


    @PostMapping("/brands")
    public ResponseEntity<DictionaryBrandResponse>
    createBrand(
            @Valid
            @RequestBody
            CreateDictionaryBrandRequest request
    ) {

        DictionaryBrandResponse createdBrand =
                dictionaryBrandService.createBrand(
                        request
                );

        return ResponseEntity
                .status(
                        HttpStatus.CREATED
                )
                .body(
                        createdBrand
                );
    }


    @PatchMapping("/brands/{brandId}")
    public ResponseEntity<DictionaryBrandResponse>
    updateBrand(
            @PathVariable Long brandId,
            @Valid
            @RequestBody
            CreateDictionaryBrandRequest request
    ) {

        return ResponseEntity.ok(
                dictionaryMutationService.updateBrand(
                        brandId,
                        request
                )
        );
    }


    @DeleteMapping("/brands/{brandId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBrand(
            @PathVariable Long brandId
    ) {

        dictionaryMutationService.deleteBrand(
                brandId
        );
    }


    @GetMapping("/brands/{brandId}/models")
    public ResponseEntity<List<DictionaryModelResponse>>
    getModelsByBrand(
            @PathVariable Long brandId
    ) {

        return ResponseEntity.ok(
                dictionaryModelService.getModelsByBrand(
                        brandId
                )
        );
    }


    @PostMapping("/brands/{brandId}/models")
    public ResponseEntity<DictionaryModelResponse>
    createModel(
            @PathVariable Long brandId,
            @Valid
            @RequestBody
            CreateDictionaryModelRequest request
    ) {

        DictionaryModelResponse createdModel =
                dictionaryModelService.createModel(
                        brandId,
                        request
                );

        return ResponseEntity
                .status(
                        HttpStatus.CREATED
                )
                .body(
                        createdModel
                );
    }


    @PatchMapping("/brands/{brandId}/models/{modelId}")
    public ResponseEntity<DictionaryModelResponse>
    updateModel(
            @PathVariable Long brandId,
            @PathVariable Long modelId,
            @Valid
            @RequestBody
            CreateDictionaryModelRequest request
    ) {

        return ResponseEntity.ok(
                dictionaryMutationService.updateModel(
                        brandId,
                        modelId,
                        request
                )
        );
    }


    @DeleteMapping("/brands/{brandId}/models/{modelId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteModel(
            @PathVariable Long brandId,
            @PathVariable Long modelId
    ) {

        dictionaryMutationService.deleteModel(
                brandId,
                modelId
        );
    }
}
