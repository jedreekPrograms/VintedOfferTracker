package pl.flipbot.marketstats;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.flipbot.bot.configuration.BotConfiguration;
import pl.flipbot.bot.configuration.BotConfigurationRepository;
import pl.flipbot.bot.configuration.TargetMode;
import pl.flipbot.dictionary.DictionaryModel;
import pl.flipbot.dictionary.DictionaryModelRepository;
import pl.flipbot.marketstats.dto.CalendarModelPlanningResponse;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MarketStatsCalendarPlanningService {

    private static final ZoneId MARKET_STATS_ZONE = ZoneId.of("Europe/Warsaw");
    private static final long CURRENT_WINDOW_FRESHNESS_MINUTES = 120L;

    private final DictionaryModelRepository modelRepository;
    private final BotConfigurationRepository configurationRepository;
    private final MarketModelScanStateRepository scanStateRepository;
    private final MarketListingObservationRepository observationRepository;

    @Transactional(readOnly = true)
    public List<CalendarModelPlanningResponse> getPlanning() {
        LocalDateTime now = LocalDateTime.now(MARKET_STATS_ZONE);
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

    private CalendarModelPlanningResponse toPlanningResponse(
            DictionaryModel model,
            List<BotConfiguration> configurations,
            LocalDateTime now
    ) {
        MarketModelScanState state = scanStateRepository
                .findById(model.getId())
                .orElse(null);

        int existingBots = safeInt(
                configurations.stream()
                        .filter(configuration -> matchesModel(model, configuration))
                        .count()
        );

        if (state == null || state.getBaselineCompleteAt() == null) {
            return new CalendarModelPlanningResponse(
                    model.getId(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    true,
                    existingBots,
                    false,
                    false,
                    false,
                    0,
                    state == null ? null : state.getLastScanAt(),
                    state != null && Boolean.TRUE.equals(state.getLastScanComplete())
            );
        }

        LocalDateTime baselineCompleteAt = state.getBaselineCompleteAt();
        MarketStatsPlanningCalculator.CalendarWindows windows =
                MarketStatsPlanningCalculator.windows(now);

        int generation = trackingGeneration(state);
        int offersToday = countPublishedListings(
                model.getId(),
                generation,
                windows.todayStart(),
                windows.now()
        );
        int offersCurrentWeek = countPublishedListings(
                model.getId(),
                generation,
                windows.currentWeekStart(),
                windows.now()
        );

        LocalDateTime lastSuccessfulScanAt = state.getLastSuccessfulScanAt();
        boolean publicationCoverageEstablished =
                state.getPublicationWindowCompleteAt() != null;
        boolean latestScanComplete =
                Boolean.TRUE.equals(state.getLastScanComplete());
        boolean successfulScanToday = lastSuccessfulScanAt != null
                && !lastSuccessfulScanAt.isBefore(windows.todayStart());
        boolean successfulScanThisWeek = lastSuccessfulScanAt != null
                && !lastSuccessfulScanAt.isBefore(windows.currentWeekStart());
        boolean currentScanFresh = lastSuccessfulScanAt != null
                && !lastSuccessfulScanAt.isBefore(
                        now.minusMinutes(CURRENT_WINDOW_FRESHNESS_MINUTES)
                );

        /*
         * A traversal of today's currently-active catalog cannot reconstruct
         * offers which were published and sold before tracking started.
         * Therefore a calendar window is exact only when the model baseline
         * was already complete at the START of that window.
         *
         * Current-day/current-week values also need a recent successful scan;
         * otherwise a stale value (especially 0) must not be presented as an
         * exact live count.
         */
        boolean todayWindowComplete = publicationCoverageEstablished
                && latestScanComplete
                && successfulScanToday
                && currentScanFresh
                && MarketStatsPlanningCalculator.coversWindowFrom(
                        baselineCompleteAt,
                        windows.todayStart()
                );
        boolean currentWeekWindowComplete = publicationCoverageEstablished
                && latestScanComplete
                && successfulScanToday
                && currentScanFresh
                && MarketStatsPlanningCalculator.coversWindowFrom(
                        baselineCompleteAt,
                        windows.currentWeekStart()
                );
        boolean previousFullWeekAvailable = publicationCoverageEstablished
                && successfulScanThisWeek
                && MarketStatsPlanningCalculator.coversWindowFrom(
                        baselineCompleteAt,
                        windows.previousWeekStart()
                );

        Integer offersPreviousFullWeek = null;
        int recommendationWeeklyOffers;
        boolean recommendationEstimated;

        int trackedDays = MarketStatsPlanningCalculator.trackedCalendarDays(
                baselineCompleteAt,
                now
        );

        if (previousFullWeekAvailable) {
            offersPreviousFullWeek = countPublishedListings(
                    model.getId(),
                    generation,
                    windows.previousWeekStart(),
                    windows.currentWeekStart()
            );
            recommendationWeeklyOffers = offersPreviousFullWeek;
            recommendationEstimated = false;
        } else {
            int observedSinceBaseline = countPublishedListings(
                    model.getId(),
                    generation,
                    baselineCompleteAt,
                    windows.now()
            );
            recommendationWeeklyOffers =
                    MarketStatsPlanningCalculator.projectWeeklyOffers(
                            observedSinceBaseline,
                            Math.max(trackedDays, 1)
                    );
            recommendationEstimated = true;
        }

        int recommendedBots = MarketStatsPlanningCalculator.recommendedBots(
                recommendationWeeklyOffers
        );

        return new CalendarModelPlanningResponse(
                model.getId(),
                state.getBaselineOfferCount(),
                offersToday,
                offersCurrentWeek,
                offersPreviousFullWeek,
                recommendedBots,
                recommendationWeeklyOffers,
                recommendationEstimated,
                existingBots,
                todayWindowComplete,
                currentWeekWindowComplete,
                previousFullWeekAvailable,
                trackedDays,
                state.getLastScanAt(),
                latestScanComplete
        );
    }

    private int countPublishedListings(
            Long modelId,
            Integer trackingGeneration,
            LocalDateTime fromInclusive,
            LocalDateTime toExclusive
    ) {
        if (fromInclusive == null
                || toExclusive == null
                || !fromInclusive.isBefore(toExclusive)) {
            return 0;
        }

        return safeInt(
                observationRepository.countPublishedListingsBetween(
                        modelId,
                        trackingGeneration,
                        fromInclusive,
                        toExclusive
                )
        );
    }

    private int trackingGeneration(MarketModelScanState state) {
        if (state == null
                || state.getTrackingGeneration() == null
                || state.getTrackingGeneration() < 1) {
            return 1;
        }
        return state.getTrackingGeneration();
    }

    private boolean matchesModel(
            DictionaryModel model,
            BotConfiguration configuration
    ) {
        if (configuration == null
                || configuration.getBot() == null
                || Boolean.TRUE.equals(configuration.getBot().getMarketStatsObserver())
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

    private TargetMode resolveTargetMode(DictionaryModel model) {
        return model.getTargetMode() == null
                ? TargetMode.VINTED_MODEL
                : model.getTargetMode();
    }

    private boolean sameText(String left, String right) {
        return left != null
                && right != null
                && normalizeText(left).equalsIgnoreCase(normalizeText(right));
    }

    private String normalizeText(String value) {
        return value.trim().replaceAll("\\s+", " ");
    }

    private int safeInt(long value) {
        return value > Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : (int) Math.max(value, 0L);
    }
}