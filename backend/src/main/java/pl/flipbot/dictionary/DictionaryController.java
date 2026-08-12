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

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        dictionaryCategoryService
                                .createCategory(
                                        request
                                )
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
                dictionaryCategoryService
                        .updateCategory(
                                categoryId,
                                request
                        )
        );
    }


    @DeleteMapping("/categories/{categoryId}")
    public ResponseEntity<Void> deleteCategory(
            @PathVariable Long categoryId
    ) {

        dictionaryCategoryService.deleteCategory(
                categoryId
        );

        return ResponseEntity.noContent().build();
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

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        dictionaryBrandService
                                .createBrand(
                                        request
                                )
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
                dictionaryBrandService
                        .updateBrand(
                                brandId,
                                request
                        )
        );
    }


    @DeleteMapping("/brands/{brandId}")
    public ResponseEntity<Void> deleteBrand(
            @PathVariable Long brandId
    ) {

        dictionaryBrandService.deleteBrand(
                brandId
        );

        return ResponseEntity.noContent().build();
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

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        dictionaryModelService
                                .createModel(
                                        brandId,
                                        request
                                )
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
                dictionaryModelService
                        .updateModel(
                                brandId,
                                modelId,
                                request
                        )
        );
    }


    @DeleteMapping("/brands/{brandId}/models/{modelId}")
    public ResponseEntity<Void> deleteModel(
            @PathVariable Long brandId,
            @PathVariable Long modelId
    ) {

        dictionaryModelService.deleteModel(
                brandId,
                modelId
        );

        return ResponseEntity.noContent().build();
    }
}
