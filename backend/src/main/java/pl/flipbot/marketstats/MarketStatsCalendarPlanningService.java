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

        int offersToday = countPublishedListings(
                model.getId(),
                windows.todayStart(),
                windows.now()
        );
        int offersCurrentWeek = countPublishedListings(
                model.getId(),
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

        /*
         * `last_scan_complete` predates publication-time backfill and therefore
         * cannot prove that calendar windows are reconstructable. The separate
         * publication-window marker is set only after a forced filtered-catalog
         * traversal completes with every required Vinted publication timestamp.
         *
         * For today/current-week figures we also require the latest complete
         * scan to be from today, so a process restart or overnight gap cannot
         * present yesterday's snapshot as current. The previous full week only
         * needs one successful scan in the current week, because that scan
         * overlaps the already-established publication history.
         */
        boolean todayWindowComplete = publicationCoverageEstablished
                && latestScanComplete
                && successfulScanToday;
        boolean currentWeekWindowComplete = todayWindowComplete;
        boolean previousFullWeekAvailable = publicationCoverageEstablished
                && successfulScanThisWeek;

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
                    windows.previousWeekStart(),
                    windows.currentWeekStart()
            );
            recommendationWeeklyOffers = offersPreviousFullWeek;
            recommendationEstimated = false;
        } else {
            int observedSinceBaseline = countPublishedListings(
                    model.getId(),
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
                        fromInclusive,
                        toExclusive
                )
        );
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
