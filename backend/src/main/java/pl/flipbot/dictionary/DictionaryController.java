package pl.flipbot.dictionary;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.flipbot.dictionary.dto.CreateDictionaryBrandRequest;
import pl.flipbot.dictionary.dto.CreateDictionaryModelRequest;
import pl.flipbot.dictionary.dto.DictionaryBrandResponse;
import pl.flipbot.dictionary.dto.DictionaryModelResponse;

import java.util.List;

@RestController
@RequestMapping(
        "/api/dictionaries"
)
@RequiredArgsConstructor
public class DictionaryController {

    private final DictionaryBrandService dictionaryBrandService;

    private final DictionaryModelService dictionaryModelService;

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
