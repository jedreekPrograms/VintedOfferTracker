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

        DictionaryBrandResponse createBrand =
                dictionaryBrandService.createBrand(
                        request
                );

        return ResponseEntity
                .status(
                        HttpStatus.CREATED
                )
                .body(
                        createBrand
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

        DictionaryModelResponse createModel =
                dictionaryModelService.createModel(
                        brandId,
                        request
                );

        return ResponseEntity
                .status(
                        HttpStatus.CREATED
                )
                .body(
                        createModel
                );

    }

}
