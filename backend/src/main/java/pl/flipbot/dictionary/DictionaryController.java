package pl.flipbot.dictionary;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.flipbot.dictionary.dto.CreateDictionaryBrandRequest;
import pl.flipbot.dictionary.dto.DictionaryBrandResponse;

import java.util.List;

@RestController
@RequestMapping(
        "/api/dictionaries"
)
@RequiredArgsConstructor
public class DictionaryController {

    private final DictionaryBrandService dictionaryBrandService;

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

}
