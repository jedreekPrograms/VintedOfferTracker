package pl.flipbot.marketstats;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.flipbot.bot.configuration.BotAdditionalTarget;
import pl.flipbot.bot.configuration.BotAdditionalTargetRepository;
import pl.flipbot.bot.configuration.BotConfiguration;
import pl.flipbot.bot.configuration.BotConfigurationRepository;
import pl.flipbot.bot.configuration.TargetMode;
import pl.flipbot.dictionary.DictionaryCategory;
import pl.flipbot.dictionary.DictionaryModel;
import pl.flipbot.dictionary.DictionaryModelRepository;
import pl.flipbot.marketstats.dto.KnownMarketListingIdsResponse;
import pl.flipbot.marketstats.dto.MarketObservationBatchRequest;
import pl.flipbot.marketstats.dto.MarketObservationBatchResponse;
import pl.flipbot.marketstats.dto.MarketStatsTargetResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MarketStatsService {

    private static final int OBSERVATION_RETENTION_DAYS = 400;
    private static final String CATEGORY_PATH_SEPARATOR_REGEX = "\\s*>\\s*";

    private final DictionaryModelRepository modelRepository;
    private final BotConfigurationRepository configurationRepository;
    private final BotAdditionalTargetRepository additionalTargetRepository;
    private final MarketModelScanStateRepository scanStateRepository;
    private final MarketListingObservationRepository observationRepository;

    @Transactional(readOnly = true)
    public List<MarketStatsTargetResponse> getTargets() {
        List<BotConfiguration> configurations = configurationRepository.findAll();
        List<BotAdditionalTarget> additionalTargets =
                additionalTargetRepository.findAll()
                        .stream()
                        .filter(target -> Boolean.TRUE.equals(target.getActive()))
                        .toList();

        return modelRepository.findAll()
                .stream()
                .sorted(
                        MarketStatsTargetOrdering.comparator(
                                configurations
                        )
                )
                .map(model -> {
                    CategoryResolution category = resolveCategory(
                            model,
                            configurations,
                            additionalTargets
                    );

                    return new MarketStatsTargetResponse(
                            model.getId(),
                            model.getBrand().getName(),
                            model.getName(),
                            resolveTargetMode(model),
                            category.path(),
                            category.resolved(),
                            model.getMarketMinPrice(),
                            model.getMarketMaxPrice(),
                            currentTrackingGeneration(model.getId())
                    );
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public KnownMarketListingIdsResponse getKnownListingIds(
            Long modelId
    ) {
        requireModel(modelId);

        LocalDateTime cutoff = LocalDateTime.now()
                .minusDays(OBSERVATION_RETENTION_DAYS);

        MarketModelScanState state = scanStateRepository
                .findById(modelId)
                .orElse(null);
        int generation = trackingGeneration(state);

        List<String> listingIds = observationRepository.findKnownListingIds(
                modelId,
                generation,
                cutoff
        );

        boolean baselineComplete = state != null
                && state.getBaselineCompleteAt() != null;

        return new KnownMarketListingIdsResponse(
                modelId,
                listingIds,
                baselineComplete
        );
    }

    @Transactional
    public void resetModelTracking(
            Long modelId
    ) {
        requireModel(modelId);

        MarketModelScanState state = scanStateRepository
                .findByModelIdForUpdate(modelId)
                .orElse(null);

        if (state == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        int nextGeneration = trackingGeneration(state) + 1;

        state.setTrackingGeneration(nextGeneration);
        state.setInitializedAt(now);
        state.setBaselineCompleteAt(null);
        state.setBaselineOfferCount(null);
        state.setPublicationWindowCompleteAt(null);
        state.setLastScanAt(now);
        state.setLastSuccessfulScanAt(null);
        state.setLastScanComplete(false);
        scanStateRepository.save(state);
    }

    @Transactional
    public MarketObservationBatchResponse recordObservations(
            Long modelId,
            MarketObservationBatchRequest request
    ) {
        Objects.requireNonNull(
                request,
                "Market observation request cannot be null"
        );

        DictionaryModel model = modelRepository.findByIdForUpdate(modelId)
                .orElseThrow(
                        () -> new NoSuchElementException(
                                "Dictionary model was not found: " + modelId
                        )
                );

        if (!samePrice(model.getMarketMinPrice(), request.minPrice())
                || !samePrice(model.getMarketMaxPrice(), request.maxPrice())) {
            throw new IllegalStateException(
                    "Market observer price range changed while model "
                            + modelId
                            + " was being scanned. The stale observation batch was rejected."
            );
        }

        List<String> listingIds = normalizeListingIds(request.listingIds());
        Map<String, BigDecimal> listingPrices = normalizeListingPrices(
                request.listingPrices(),
                listingIds
        );

        if (listingIds.isEmpty() && !request.complete()) {
            throw new IllegalArgumentException(
                    "An incomplete market scan must contain at least one marketplace listing id."
            );
        }

        LocalDateTime now = LocalDateTime.now();

        MarketModelScanState state = scanStateRepository
                .findByModelIdForUpdate(modelId)
                .orElse(null);

        int currentGeneration = trackingGeneration(state);
        int requestedGeneration = request.trackingGeneration() == null
                ? 1
                : request.trackingGeneration();

        if (requestedGeneration != currentGeneration) {
            throw new IllegalStateException(
                    "Stale market observer batch rejected for model "
                            + modelId
                            + ": request generation="
                            + requestedGeneration
                            + ", current generation="
                            + currentGeneration
            );
        }

        boolean createdState = state == null;

        if (state == null) {
            state = MarketModelScanState.builder()
                    .model(model)
                    .initializedAt(now)
                    .trackingGeneration(1)
                    .baselineCompleteAt(null)
                    .baselineOfferCount(null)
                    .lastScanAt(now)
                    .lastSuccessfulScanAt(null)
                    .lastScanComplete(false)
                    .build();

            state = scanStateRepository.saveAndFlush(state);
        }

        boolean baselineMode = state.getBaselineCompleteAt() == null;
        int generation = trackingGeneration(state);

        Map<String, MarketListingObservation> existingById =
                new HashMap<>();

        if (!listingIds.isEmpty()) {
            observationRepository
                    .findAllByModel_IdAndTrackingGenerationAndMarketplaceListingIdIn(
                            modelId,
                            generation,
                            listingIds
                    )
                    .forEach(observation -> existingById.put(
                            observation.getMarketplaceListingId(),
                            observation
                    ));
        }

        List<MarketListingObservation> changed = new ArrayList<>();
        int newListings = 0;

        for (String listingId : listingIds) {
            MarketListingObservation existing = existingById.get(listingId);

            BigDecimal observedPrice = listingPrices.get(listingId);

            if (existing != null) {
                existing.setLastSeenAt(now);
                applyObservedPrice(existing, observedPrice);
                changed.add(existing);
                continue;
            }

            boolean baseline = baselineMode;

            MarketListingObservation observation =
                    MarketListingObservation.builder()
                            .model(model)
                            .trackingGeneration(generation)
                            .marketplaceListingId(listingId)
                            .firstSeenAt(now)
                            .lastSeenAt(now)
                            .baseline(baseline)
                            .build();

            applyObservedPrice(observation, observedPrice);
            changed.add(observation);

            if (!baseline) {
                newListings++;
            }
        }

        if (!changed.isEmpty()) {
            observationRepository.saveAll(changed);
        }

        state.setLastScanAt(now);
        state.setLastScanComplete(request.complete());

        if (request.complete()) {
            state.setLastSuccessfulScanAt(now);

            if (state.getBaselineCompleteAt() == null) {
                observationRepository.flush();
                state.setBaselineCompleteAt(now);
                state.setBaselineOfferCount(
                        safeInt(
                                observationRepository.countByModel_IdAndTrackingGenerationAndBaselineTrue(
                                        modelId,
                                        generation
                                )
                        )
                );
            }
        }

        scanStateRepository.save(state);

        observationRepository.deleteByLastSeenAtBefore(
                now.minusDays(OBSERVATION_RETENTION_DAYS)
        );

        return new MarketObservationBatchResponse(
                modelId,
                createdState || baselineMode,
                listingIds.size(),
                newListings,
                now,
                request.complete()
        );
    }

    private CategoryResolution resolveCategory(
            DictionaryModel model,
            List<BotConfiguration> configurations,
            List<BotAdditionalTarget> additionalTargets
    ) {
        DictionaryCategory dictionaryCategory = model.getCategory();

        if (dictionaryCategory != null
                && dictionaryCategory.getPath() != null
                && !dictionaryCategory.getPath().isBlank()) {
            return new CategoryResolution(
                    splitCategoryPath(dictionaryCategory.getPath()),
                    true
            );
        }

        List<List<String>> paths = new ArrayList<>();

        configurations.stream()
                .filter(configuration -> matchesModel(model, configuration))
                .map(BotConfiguration::getCategoryPath)
                .filter(Objects::nonNull)
                .filter(path -> !path.isEmpty())
                .map(List::copyOf)
                .forEach(paths::add);

        additionalTargets.stream()
                .filter(target -> matchesModel(model, target))
                .map(BotAdditionalTarget::getCategoryPath)
                .filter(Objects::nonNull)
                .filter(path -> !path.isEmpty())
                .map(List::copyOf)
                .forEach(paths::add);

        if (paths.isEmpty()) {
            return new CategoryResolution(
                    List.of(),
                    false
            );
        }

        List<String> first = paths.getFirst();

        boolean allEqual = paths.stream()
                .allMatch(path -> samePath(first, path));

        if (!allEqual) {
            return new CategoryResolution(
                    List.of(),
                    false
            );
        }

        return new CategoryResolution(
                List.copyOf(first),
                true
        );
    }

    private List<String> splitCategoryPath(
            String storedPath
    ) {
        if (storedPath == null || storedPath.isBlank()) {
            return List.of();
        }

        return Arrays.stream(
                        storedPath.split(CATEGORY_PATH_SEPARATOR_REGEX)
                )
                .map(String::trim)
                .filter(element -> !element.isBlank())
                .toList();
    }

    private boolean matchesModel(
            DictionaryModel model,
            BotConfiguration configuration
    ) {
        if (configuration == null
                || configuration.getBot() == null
                || Boolean.TRUE.equals(
                configuration.getBot().getMarketStatsObserver()
        )
                || !sameText(
                model.getBrand().getName(),
                configuration.getBrand()
        )) {
            return false;
        }

        TargetMode modelMode = resolveTargetMode(model);
        TargetMode configurationMode = configuration.getTargetMode() == null
                ? TargetMode.VINTED_MODEL
                : configuration.getTargetMode();

        if (modelMode != configurationMode) {
            return false;
        }

        return switch (modelMode) {
            case VINTED_MODEL -> sameText(
                    model.getName(),
                    configuration.getModel()
            );
            case SEARCH_QUERY -> sameText(
                    model.getName(),
                    configuration.getSearchQuery()
            );
        };
    }

    private boolean matchesModel(
            DictionaryModel model,
            BotAdditionalTarget target
    ) {
        if (target == null
                || !Boolean.TRUE.equals(target.getActive())
                || target.getConfiguration() == null
                || target.getConfiguration().getBot() == null
                || Boolean.TRUE.equals(
                        target.getConfiguration().getBot().getMarketStatsObserver()
                )
                || !sameText(
                        model.getBrand().getName(),
                        target.getBrand()
                )) {
            return false;
        }

        TargetMode modelMode = resolveTargetMode(model);
        TargetMode targetMode = target.getTargetMode() == null
                ? TargetMode.VINTED_MODEL
                : target.getTargetMode();

        if (modelMode != targetMode) {
            return false;
        }

        return switch (modelMode) {
            case VINTED_MODEL -> sameText(
                    model.getName(),
                    target.getModel()
            );
            case SEARCH_QUERY -> sameText(
                    model.getName(),
                    target.getSearchQuery()
            );
        };
    }

    private TargetMode resolveTargetMode(
            DictionaryModel model
    ) {
        return model.getTargetMode() == null
                ? TargetMode.VINTED_MODEL
                : model.getTargetMode();
    }

    private DictionaryModel requireModel(
            Long modelId
    ) {
        if (modelId == null || modelId <= 0) {
            throw new IllegalArgumentException(
                    "Model id must be positive."
            );
        }

        return modelRepository.findById(modelId)
                .orElseThrow(
                        () -> new NoSuchElementException(
                                "Dictionary model was not found: " + modelId
                        )
                );
    }

    private int currentTrackingGeneration(Long modelId) {
        return scanStateRepository.findById(modelId)
                .map(this::trackingGeneration)
                .orElse(1);
    }

    private int trackingGeneration(MarketModelScanState state) {
        if (state == null
                || state.getTrackingGeneration() == null
                || state.getTrackingGeneration() < 1) {
            return 1;
        }
        return state.getTrackingGeneration();
    }

    private void applyObservedPrice(
            MarketListingObservation observation,
            BigDecimal observedPrice
    ) {
        if (observation == null
                || observedPrice == null
                || observedPrice.signum() <= 0) {
            return;
        }

        BigDecimal normalized = observedPrice.setScale(
                2,
                java.math.RoundingMode.HALF_UP
        );

        if (observation.getFirstSeenPrice() == null) {
            observation.setFirstSeenPrice(normalized);
        }

        observation.setLatestPrice(normalized);

        if (observation.getLowestSeenPrice() == null
                || normalized.compareTo(observation.getLowestSeenPrice()) < 0) {
            observation.setLowestSeenPrice(normalized);
        }

        if (observation.getHighestSeenPrice() == null
                || normalized.compareTo(observation.getHighestSeenPrice()) > 0) {
            observation.setHighestSeenPrice(normalized);
        }
    }

    private Map<String, BigDecimal> normalizeListingPrices(
            Map<String, BigDecimal> rawPrices,
            List<String> acceptedListingIds
    ) {
        if (rawPrices == null
                || rawPrices.isEmpty()
                || acceptedListingIds == null
                || acceptedListingIds.isEmpty()) {
            return Map.of();
        }

        Set<String> accepted = Set.copyOf(acceptedListingIds);
        Map<String, BigDecimal> normalized = new LinkedHashMap<>();

        for (Map.Entry<String, BigDecimal> entry : rawPrices.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }

            String listingId = entry.getKey().trim();
            BigDecimal price = entry.getValue();

            if (!listingId.isEmpty()
                    && accepted.contains(listingId)
                    && price.signum() > 0) {
                normalized.put(listingId, price);
            }
        }

        return Map.copyOf(normalized);
    }

    private List<String> normalizeListingIds(
            List<String> rawListingIds
    ) {
        if (rawListingIds == null) {
            return List.of();
        }

        LinkedHashSet<String> unique = new LinkedHashSet<>();

        for (String rawId : rawListingIds) {
            if (rawId == null) {
                continue;
            }

            String normalized = rawId.trim();

            if (!normalized.isEmpty()) {
                unique.add(normalized);
            }
        }

        return List.copyOf(unique);
    }

    private boolean sameText(
            String left,
            String right
    ) {
        return left != null
                && right != null
                && normalizeText(left).equalsIgnoreCase(
                normalizeText(right)
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
            if (!sameText(left.get(index), right.get(index))) {
                return false;
            }
        }

        return true;
    }

    private String normalizeText(
            String value
    ) {
        return value.trim().replaceAll("\\s+", " ");
    }

    private int safeInt(
            long value
    ) {
        return value > Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : (int) value;
    }

    private record CategoryResolution(
            List<String> path,
            boolean resolved
    ) {
    }
}