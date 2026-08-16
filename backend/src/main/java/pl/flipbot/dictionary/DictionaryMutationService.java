package pl.flipbot.dictionary;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.flipbot.bot.BotStatus;
import pl.flipbot.bot.configuration.BotConfiguration;
import pl.flipbot.bot.configuration.BotConfigurationRepository;
import pl.flipbot.bot.configuration.TargetMode;
import pl.flipbot.dictionary.dto.CreateDictionaryBrandRequest;
import pl.flipbot.dictionary.dto.CreateDictionaryCategoryRequest;
import pl.flipbot.dictionary.dto.CreateDictionaryModelRequest;
import pl.flipbot.dictionary.dto.DictionaryBrandResponse;
import pl.flipbot.dictionary.dto.DictionaryCategoryResponse;
import pl.flipbot.dictionary.dto.DictionaryModelResponse;
import pl.flipbot.dictionary.dto.UpdateDictionaryModelPricingRequest;
import pl.flipbot.listing.ListingRepository;
import pl.flipbot.listing.ListingStatus;
import pl.flipbot.marketstats.MarketStatsService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DictionaryMutationService {

    private static final String PATH_SEPARATOR = " > ";

    private final DictionaryBrandRepository brandRepository;
    private final DictionaryModelRepository modelRepository;
    private final DictionaryCategoryRepository categoryRepository;
    private final BotConfigurationRepository configurationRepository;
    private final ListingRepository listingRepository;
    private final DictionaryModelService dictionaryModelService;
    private final MarketStatsService marketStatsService;


    @Transactional
    public DictionaryBrandResponse updateBrand(
            Long brandId,
            CreateDictionaryBrandRequest request
    ) {

        DictionaryBrand brand = brandRepository.findById(brandId)
                .orElseThrow(() -> new DictionaryEntryNotFoundException(
                        "Brand was not found: " + brandId
                ));

        String oldName = brand.getName();
        String newName = normalizeName(request.getName(), "Brand");

        boolean duplicate = brandRepository.findAll().stream()
                .anyMatch(other -> !other.getId().equals(brandId)
                        && other.getName().equalsIgnoreCase(newName));

        if (duplicate) {
            throw new DictionaryEntryAlreadyExistsException(
                    "Brand already exists: " + newName
            );
        }

        List<BotConfiguration> affected = configurationRepository.findAll().stream()
                .filter(configuration -> sameText(configuration.getBrand(), oldName))
                .toList();

        ensureConfigurationsCanBeChanged(affected);

        brand.setName(newName);
        affected.forEach(configuration -> configuration.setBrand(newName));

        return new DictionaryBrandResponse(
                brand.getId(),
                brand.getName()
        );
    }


    @Transactional
    public void deleteBrand(
            Long brandId
    ) {

        DictionaryBrand brand = brandRepository.findById(brandId)
                .orElseThrow(() -> new DictionaryEntryNotFoundException(
                        "Brand was not found: " + brandId
                ));

        if (!modelRepository.findAllByBrand_IdOrderByNameAsc(brandId).isEmpty()) {
            throw new IllegalStateException(
                    "Brand cannot be deleted while it still has models. Delete or move the models first."
            );
        }

        boolean usedByBot = configurationRepository.findAll().stream()
                .anyMatch(configuration -> sameText(
                        configuration.getBrand(),
                        brand.getName()
                ));

        if (usedByBot) {
            throw new IllegalStateException(
                    "Brand cannot be deleted because it is used by at least one bot configuration."
            );
        }

        brandRepository.delete(brand);
    }


    @Transactional
    public DictionaryModelResponse updateModel(
            Long brandId,
            Long modelId,
            CreateDictionaryModelRequest request
    ) {

        DictionaryBrand brand = brandRepository.findById(brandId)
                .orElseThrow(() -> new DictionaryEntryNotFoundException(
                        "Brand was not found: " + brandId
                ));

        DictionaryModel model = modelRepository.findById(modelId)
                .orElseThrow(() -> new DictionaryEntryNotFoundException(
                        "Model was not found: " + modelId
                ));

        ensureModelBelongsToBrand(
                model,
                brandId
        );

        String oldName = model.getName();
        String newName = normalizeName(request.getName(), "Model");
        TargetMode oldTargetMode = resolveModelTargetMode(model);
        TargetMode newTargetMode = request.getTargetMode() == null
                ? oldTargetMode
                : request.getTargetMode();

        boolean duplicate = modelRepository
                .findAllByBrand_IdOrderByNameAsc(brandId)
                .stream()
                .anyMatch(other -> !other.getId().equals(modelId)
                        && other.getName().equalsIgnoreCase(newName));

        if (duplicate) {
            throw new DictionaryEntryAlreadyExistsException(
                    "Model already exists for brand "
                            + brand.getName()
                            + ": "
                            + newName
            );
        }

        List<BotConfiguration> affected = configurationRepository.findAll().stream()
                .filter(configuration -> sameText(
                        configuration.getBrand(),
                        brand.getName()
                ))
                .filter(configuration -> configurationUsesModelName(
                        configuration,
                        oldName
                ))
                .toList();

        boolean targetDefinitionChanged =
                !sameText(oldName, newName)
                        || oldTargetMode != newTargetMode;

        if (targetDefinitionChanged) {
            ensureConfigurationsCanBeChanged(affected);
        }

        model.setName(newName);
        model.setTargetMode(newTargetMode);

        if (targetDefinitionChanged) {
            affected.forEach(configuration -> applyModelTarget(
                    configuration,
                    newTargetMode,
                    newName
            ));
        }

        return dictionaryModelService.map(model);
    }


    @Transactional
    public DictionaryModelResponse updateModelPricing(
            Long brandId,
            Long modelId,
            UpdateDictionaryModelPricingRequest request
    ) {

        DictionaryModel model = modelRepository.findById(modelId)
                .orElseThrow(() -> new DictionaryEntryNotFoundException(
                        "Model was not found: " + modelId
                ));

        ensureModelBelongsToBrand(
                model,
                brandId
        );

        BigDecimal marketMinPrice = request.getMarketMinPrice();
        BigDecimal marketMaxPrice = request.getMarketMaxPrice();

        if (marketMinPrice != null
                && marketMaxPrice != null
                && marketMinPrice.compareTo(marketMaxPrice) > 0) {
            throw new IllegalArgumentException(
                    "Market minimum price cannot be greater than market maximum price."
            );
        }

        boolean marketRangeChanged =
                !samePrice(model.getMarketMinPrice(), marketMinPrice)
                        || !samePrice(model.getMarketMaxPrice(), marketMaxPrice);

        model.setProposedOfferPrice(
                request.getProposedOfferPrice()
        );

        model.setExpectedResalePrice(
                request.getExpectedResalePrice()
        );

        model.setMarketMinPrice(marketMinPrice);
        model.setMarketMaxPrice(marketMaxPrice);

        if (marketRangeChanged) {
            marketStatsService.resetModelTracking(modelId);
        }

        return dictionaryModelService.map(model);
    }


    @Transactional
    public void deleteModel(
            Long brandId,
            Long modelId
    ) {

        DictionaryModel model = modelRepository.findById(modelId)
                .orElseThrow(() -> new DictionaryEntryNotFoundException(
                        "Model was not found: " + modelId
                ));

        ensureModelBelongsToBrand(
                model,
                brandId
        );

        boolean usedByBot = configurationRepository.findAll().stream()
                .filter(configuration -> sameText(
                        configuration.getBrand(),
                        model.getBrand().getName()
                ))
                .anyMatch(configuration -> configurationUsesModelName(
                        configuration,
                        model.getName()
                ));

        if (usedByBot) {
            throw new IllegalStateException(
                    "Model cannot be deleted because it is used by at least one bot configuration."
            );
        }

        modelRepository.delete(model);
    }


    @Transactional
    public DictionaryCategoryResponse updateCategory(
            Long categoryId,
            CreateDictionaryCategoryRequest request
    ) {

        DictionaryCategory category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new DictionaryEntryNotFoundException(
                        "Category was not found: " + categoryId
                ));

        List<String> oldPath = splitPath(category.getPath());
        List<String> newPath = normalizeCategoryPath(request.getCategoryPath());
        String newStoredPath = String.join(PATH_SEPARATOR, newPath);

        boolean duplicate = categoryRepository.findAll().stream()
                .anyMatch(other -> !other.getId().equals(categoryId)
                        && other.getPath().equalsIgnoreCase(newStoredPath));

        if (duplicate) {
            throw new DictionaryEntryAlreadyExistsException(
                    "Category already exists: " + newStoredPath
            );
        }

        List<BotConfiguration> affected = configurationRepository.findAll().stream()
                .filter(configuration -> samePath(
                        configuration.getCategoryPath(),
                        oldPath
                ))
                .toList();

        ensureConfigurationsCanBeChanged(affected);

        category.setPath(newStoredPath);
        category.setName(newPath.get(newPath.size() - 1));

        affected.forEach(configuration ->
                configuration.setCategoryPath(
                        new ArrayList<>(newPath)
                )
        );

        return new DictionaryCategoryResponse(
                category.getId(),
                category.getName(),
                category.getPath(),
                newPath
        );
    }


    @Transactional
    public void deleteCategory(
            Long categoryId
    ) {

        DictionaryCategory category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new DictionaryEntryNotFoundException(
                        "Category was not found: " + categoryId
                ));

        List<String> categoryPath = splitPath(category.getPath());

        boolean usedByBot = configurationRepository.findAll().stream()
                .anyMatch(configuration -> samePath(
                        configuration.getCategoryPath(),
                        categoryPath
                ));

        if (usedByBot) {
            throw new IllegalStateException(
                    "Category cannot be deleted because it is used by at least one bot configuration."
            );
        }

        categoryRepository.delete(category);
    }


    private void ensureConfigurationsCanBeChanged(
            List<BotConfiguration> configurations
    ) {

        for (BotConfiguration configuration : configurations) {

            if (configuration.getBot().getStatus() != BotStatus.STOPPED) {
                throw new IllegalStateException(
                        "Dictionary entry cannot be edited while it is used by a running bot."
                );
            }

            Long botId = configuration.getBot().getId();

            boolean hasNegotiating = !listingRepository
                    .findByBotIdAndStatusOrderByIdAsc(
                            botId,
                            ListingStatus.NEGOTIATING
                    )
                    .isEmpty();

            boolean hasActionRequired = !listingRepository
                    .findByBotIdAndStatusOrderByIdAsc(
                            botId,
                            ListingStatus.ACTION_REQUIRED
                    )
                    .isEmpty();

            if (hasNegotiating || hasActionRequired) {
                throw new IllegalStateException(
                        "Dictionary entry cannot be edited while a using bot has active negotiations or action-required listings."
                );
            }
        }
    }


    private void ensureModelBelongsToBrand(
            DictionaryModel model,
            Long brandId
    ) {

        if (!model.getBrand().getId().equals(brandId)) {
            throw new DictionaryEntryNotFoundException(
                    "Model "
                            + model.getId()
                            + " does not belong to brand "
                            + brandId
            );
        }
    }


    private boolean configurationUsesModelName(
            BotConfiguration configuration,
            String modelName
    ) {

        TargetMode targetMode = configuration.getTargetMode() == null
                ? TargetMode.VINTED_MODEL
                : configuration.getTargetMode();

        if (targetMode == TargetMode.SEARCH_QUERY) {
            return sameText(
                    configuration.getSearchQuery(),
                    modelName
            );
        }

        return sameText(
                configuration.getModel(),
                modelName
        );
    }


    private void applyModelTarget(
            BotConfiguration configuration,
            TargetMode targetMode,
            String modelName
    ) {

        configuration.setTargetMode(targetMode);

        if (targetMode == TargetMode.SEARCH_QUERY) {
            configuration.setModel(null);
            configuration.setSearchQuery(modelName);
            return;
        }

        configuration.setModel(modelName);
        configuration.setSearchQuery(null);
    }


    private TargetMode resolveModelTargetMode(
            DictionaryModel model
    ) {

        return model.getTargetMode() == null
                ? TargetMode.VINTED_MODEL
                : model.getTargetMode();
    }


    private String normalizeName(
            String value,
            String label
    ) {

        if (value == null) {
            throw new IllegalArgumentException(
                    label + " name cannot be null"
            );
        }

        String normalized = value
                .trim()
                .replaceAll("\\s+", " ");

        if (normalized.isBlank()) {
            throw new IllegalArgumentException(
                    label + " name cannot be blank"
            );
        }

        return normalized;
    }


    private List<String> normalizeCategoryPath(
            List<String> categoryPath
    ) {

        if (categoryPath == null || categoryPath.isEmpty()) {
            throw new IllegalArgumentException(
                    "Category path cannot be empty"
            );
        }

        return categoryPath.stream()
                .map(element -> normalizeName(
                        element,
                        "Category path element"
                ))
                .peek(element -> {
                    if (element.contains(">")) {
                        throw new IllegalArgumentException(
                                "Category path element cannot contain the '>' character: "
                                        + element
                        );
                    }
                })
                .toList();
    }


    private List<String> splitPath(
            String path
    ) {

        return Arrays.stream(
                        path.split("\\s*>\\s*")
                )
                .map(String::trim)
                .filter(element -> !element.isBlank())
                .toList();
    }


    private boolean sameText(
            String left,
            String right
    ) {

        return left != null
                && right != null
                && left.trim().equalsIgnoreCase(
                        right.trim()
                );
    }


    private boolean samePrice(
            BigDecimal left,
            BigDecimal right
    ) {
        if (left == null || right == null) {
            return left == right;
        }

        return left.compareTo(right) == 0;
    }


    private boolean samePath(
            List<String> left,
            List<String> right
    ) {

        if (left == null
                || right == null
                || left.size() != right.size()) {
            return false;
        }

        for (int index = 0; index < left.size(); index++) {
            if (!sameText(
                    left.get(index),
                    right.get(index)
            )) {
                return false;
            }
        }

        return true;
    }
}
