package pl.flipbot.marketstats;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import pl.flipbot.marketstats.dto.ModelPlanningResponse;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class MarketStatsService {

    private static final int TRACKING_WINDOW_DAYS = 7;
    private static final int NEW_CONVERSATIONS_PER_BOT_PER_DAY = 5;
    private static final int OBSERVATION_RETENTION_DAYS = 30;
    private static final String CATEGORY_PATH_SEPARATOR_REGEX = "\\s*>\\s*";

    private final DictionaryModelRepository modelRepository;
    private final BotConfigurationRepository configurationRepository;
    private final MarketModelScanStateRepository scanStateRepository;
    private final MarketListingObservationRepository observationRepository;

    @Transactional(readOnly = true)
    public List<ModelPlanningResponse> getPlanning() {
        LocalDateTime now = LocalDateTime.now();
        List<BotConfiguration> configurations = configurationRepository.findAll();

        return modelRepository.findAll()
                .stream()
                .sorted(
                        Comparator.comparing(
                                        (DictionaryModel model) -> model.getBrand().getName(),
                                        String.CASE_INSENSITIVE_ORDER
                                )
                                .thenComparing(
                                        DictionaryModel::getName,
                                        String.CASE_INSENSITIVE_ORDER
                                )
                )
                .map(model -> toPlanningResponse(
                        model,
                        configurations,
                        now
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MarketStatsTargetResponse> getTargets() {
        List<BotConfiguration> configurations = configurationRepository.findAll();

        return modelRepository.findAll()
                .stream()
                .sorted(
                        Comparator.comparing(
                                        (DictionaryModel model) -> model.getBrand().getName(),
                                        String.CASE_INSENSITIVE_ORDER
                                )
                                .thenComparing(
                                        DictionaryModel::getName,
                                        String.CASE_INSENSITIVE_ORDER
                                )
                )
                .map(model -> {
                    CategoryResolution category = resolveCategory(
                            model,
                            configurations
                    );

                    return new MarketStatsTargetResponse(
                            model.getId(),
                            model.getBrand().getName(),
                            model.getName(),
                            resolveTargetMode(model),
                            category.path(),
                            category.resolved(),
                            model.getMarketMinPrice(),
                            model.getMarketMaxPrice()
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

        List<String> listingIds = observationRepository.findKnownListingIds(
                modelId,
                cutoff
        );

        boolean baselineComplete = scanStateRepository
                .findById(modelId)
                .map(MarketModelScanState::getBaselineCompleteAt)
                .isPresent();

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

        observationRepository.deleteByModel_Id(modelId);

        if (state != null) {
            scanStateRepository.delete(state);
        }
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

        if (listingIds.isEmpty() && !request.complete()) {
            throw new IllegalArgumentException(
                    "An incomplete market scan must contain at least one marketplace listing id."
            );
        }

        LocalDateTime now = LocalDateTime.now();

        MarketModelScanState state = scanStateRepository
                .findByModelIdForUpdate(modelId)
                .orElse(null);

        boolean createdState = state == null;

        if (state == null) {
            state = MarketModelScanState.builder()
                    .model(model)
                    .initializedAt(now)
                    .baselineCompleteAt(null)
                    .lastScanAt(now)
                    .lastSuccessfulScanAt(null)
                    .lastScanComplete(false)
                    .build();

            state = scanStateRepository.saveAndFlush(state);
        }

        boolean baselineMode = state.getBaselineCompleteAt() == null;

        Map<String, MarketListingObservation> existingById =
                new HashMap<>();

        if (!listingIds.isEmpty()) {
            observationRepository
                    .findAllByModel_IdAndMarketplaceListingIdIn(
                            modelId,
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

            if (existing != null) {
                existing.setLastSeenAt(now);
                changed.add(existing);
                continue;
            }

            boolean baseline = baselineMode;

            changed.add(
                    MarketListingObservation.builder()
                            .model(model)
                            .marketplaceListingId(listingId)
                            .firstSeenAt(now)
                            .lastSeenAt(now)
                            .baseline(baseline)
                            .build()
            );

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
                state.setBaselineCompleteAt(now);
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

    private ModelPlanningResponse toPlanningResponse(
            DictionaryModel model,
            List<BotConfiguration> configurations,
            LocalDateTime now
    ) {
        MarketModelScanState state = scanStateRepository
                .findById(model.getId())
                .orElse(null);

        int existingBots = safeInt(
                configurations.stream()
                        .filter(configuration -> matchesModel(
                                model,
                                configuration
                        ))
                        .count()
        );

        if (state == null || state.getBaselineCompleteAt() == null) {
            return new ModelPlanningResponse(
                    model.getId(),
                    null,
                    null,
                    null,
                    null,
                    existingBots,
                    false,
                    0,
                    state == null ? null : state.getLastScanAt(),
                    state != null && Boolean.TRUE.equals(state.getLastScanComplete())
            );
        }

        int baselineOffers = safeInt(
                observationRepository.countByModel_IdAndBaselineTrue(
                        model.getId()
                )
        );

        long trackedHours = Math.max(
                0L,
                Duration.between(
                        state.getBaselineCompleteAt(),
                        now
                ).toHours()
        );

        int trackedDays = Math.min(
                TRACKING_WINDOW_DAYS,
                (int) (trackedHours / 24L)
        );

        boolean statsReady = trackedHours >= TRACKING_WINDOW_DAYS * 24L;

        int offersLast24Hours = safeInt(
                observationRepository
                        .countByModel_IdAndBaselineFalseAndFirstSeenAtAfter(
                                model.getId(),
                                now.minusHours(24L)
                        )
        );

        Integer offersLast7Days = null;
        Integer recommendedBots = null;

        if (statsReady) {
            long offers = observationRepository
                    .countByModel_IdAndBaselineFalseAndFirstSeenAtAfter(
                            model.getId(),
                            now.minusDays(TRACKING_WINDOW_DAYS)
                    );

            offersLast7Days = safeInt(offers);
            recommendedBots = calculateRecommendedBots(offersLast7Days);
        }

        return new ModelPlanningResponse(
                model.getId(),
                baselineOffers,
                offersLast24Hours,
                offersLast7Days,
                recommendedBots,
                existingBots,
                statsReady,
                trackedDays,
                state.getLastScanAt(),
                Boolean.TRUE.equals(state.getLastScanComplete())
        );
    }

    private int calculateRecommendedBots(
            int offersLast7Days
    ) {
        int weeklyCapacityPerBot =
                TRACKING_WINDOW_DAYS
                        * NEW_CONVERSATIONS_PER_BOT_PER_DAY;

        if (offersLast7Days <= 0) {
            return 0;
        }

        return (offersLast7Days + weeklyCapacityPerBot - 1)
                / weeklyCapacityPerBot;
    }

    private CategoryResolution resolveCategory(
            DictionaryModel model,
            List<BotConfiguration> configurations
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

        List<List<String>> paths = configurations.stream()
                .filter(configuration -> matchesModel(model, configuration))
                .map(BotConfiguration::getCategoryPath)
                .filter(Objects::nonNull)
                .filter(path -> !path.isEmpty())
                .map(path -> List.copyOf(path))
                .toList();

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
