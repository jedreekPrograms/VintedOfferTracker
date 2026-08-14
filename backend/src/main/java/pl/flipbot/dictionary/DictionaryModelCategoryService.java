package pl.flipbot.dictionary;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.flipbot.dictionary.dto.DictionaryModelResponse;
import pl.flipbot.dictionary.dto.UpdateDictionaryModelCategoryRequest;

@Service
@RequiredArgsConstructor
public class DictionaryModelCategoryService {

    private final DictionaryModelRepository modelRepository;
    private final DictionaryCategoryRepository categoryRepository;
    private final DictionaryModelService modelService;

    @Transactional
    public DictionaryModelResponse updateCategory(
            Long brandId,
            Long modelId,
            UpdateDictionaryModelCategoryRequest request
    ) {
        DictionaryModel model = modelRepository.findById(modelId)
                .orElseThrow(() -> new DictionaryEntryNotFoundException(
                        "Model was not found: " + modelId
                ));

        if (!model.getBrand().getId().equals(brandId)) {
            throw new DictionaryEntryNotFoundException(
                    "Model " + modelId + " does not belong to brand " + brandId
            );
        }

        DictionaryCategory category = categoryRepository
                .findById(request.getCategoryId())
                .orElseThrow(() -> new DictionaryEntryNotFoundException(
                        "Category was not found: " + request.getCategoryId()
                ));

        model.setCategory(category);

        return modelService.map(model);
    }
}
