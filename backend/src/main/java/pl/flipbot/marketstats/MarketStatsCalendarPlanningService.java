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
import pl.flipbot.negotiation.audit.RealActionAudit;
import pl.flipbot.negotiation.audit.RealActionAuditOutcome;
import pl.flipbot.negotiation.audit.RealActionAuditRepository;
import pl.flipbot.negotiation.guard.RealActionType;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class MarketStatsCalendarPlanningService {

    private static final ZoneId MARKET_STATS_ZONE = ZoneId.of("Europe/Warsaw");

    private final DictionaryModelRepository modelRepository;
    private final BotConfigurationRepository configurationRepository;
    private final MarketModelScanStateRepository scanStateRepository;
    private final MarketListingObservationRepository observationRepository;
    private final RealActionAuditRepository realActionAuditRepository;

    @Transactional(readOnly = true)
    public List<CalendarModelPlanningResponse> getPlanning() {
        LocalDateTime now = LocalDateTime.now(MARKET_STATS_ZONE);
        List<BotConfiguration> configurations = configurationRepository.findAll();
        MarketStatsPlanningCalculator.CalendarWindows windows =
                MarketStatsPlanningCalculator.windows(now);
        NegotiationStartsByBot negotiationStarts =
                loadNegotiationStarts(windows);

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
                        now,
                        windows,
                        negotiationStarts
                ))
                .toList();
    }

    private CalendarModelPlanningResponse toPlanningResponse(
            DictionaryModel model,
            List<BotConfiguration> configurations,
            LocalDateTime now,
            MarketStatsPlanningCalculator.CalendarWindows windows,
            NegotiationStartsByBot negotiationStarts
    ) {
        MarketModelScanState state = scanStateRepository
                .findById(model.getId())
                .orElse(null);

        List<Long> matchingBotIds = configurations.stream()
                .filter(configuration -> matchesModel(model, configuration))
                .map(BotConfiguration::getBot)
                .filter(Objects::nonNull)
                .map(bot -> bot.getId())
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        int existingBots = matchingBotIds.size();
        int negotiationsStartedToday = sumNegotiationStarts(
                matchingBotIds,
                negotiationStarts.todayByBot()
        );
        int negotiationsStartedCurrentWeek = sumNegotiationStarts(
                matchingBotIds,
                negotiationStarts.currentWeekByBot()
        );

        if (state == null || state.getBaselineCompleteAt() == null) {
            return new CalendarModelPlanningResponse(
                    model.getId(),
                    null,
                    null,
                    null,
                    null,
                    negotiationsStartedToday,
                    negotiationsStartedCurrentWeek,
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

        int offersToday = countNewListings(
                model.getId(),
                windows.todayStart(),
                windows.now()
        );
        int offersCurrentWeek = countNewListings(
                model.getId(),
                windows.currentWeekStart(),
                windows.now()
        );

        boolean todayWindowComplete =
                MarketStatsPlanningCalculator.coversWindowFrom(
                        baselineCompleteAt,
                        windows.todayStart()
                );
        boolean currentWeekWindowComplete =
                MarketStatsPlanningCalculator.coversWindowFrom(
                        baselineCompleteAt,
                        windows.currentWeekStart()
                );
        boolean previousFullWeekAvailable =
                MarketStatsPlanningCalculator.coversWindowFrom(
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
            /*
             * For a completed calendar week we want the set of unique market
             * opportunities that actually existed at any point during that
             * week, not only ids whose first-ever observation happened inside
             * the week. This lets the planner reuse the historical observation
             * data we already have: listings known before Monday but still
             * present during the week seed the weekly inventory, while newly
             * observed ids are naturally added once because observations are
             * unique per model + marketplace listing id.
             */
            offersPreviousFullWeek = countObservedListings(
                    model.getId(),
                    windows.previousWeekStart(),
                    windows.currentWeekStart()
            );
            recommendationWeeklyOffers = offersPreviousFullWeek;
            recommendationEstimated = false;
        } else {
            int observedSinceBaseline = countNewListings(
                    model.getId(),
                    baselineCompleteAt,
                    windows.now()
            );
            recommendationWeeklyOffers =
                    MarketStatsPlanningCalculator.projectWeeklyOffers(
                            observedSinceBaseline,
                            trackedDays
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
                negotiationsStartedToday,
                negotiationsStartedCurrentWeek,
                recommendedBots,
                recommendationWeeklyOffers,
                recommendationEstimated,
                existingBots,
                todayWindowComplete,
                currentWeekWindowComplete,
                previousFullWeekAvailable,
                trackedDays,
                state.getLastScanAt(),
                Boolean.TRUE.equals(state.getLastScanComplete())
        );
    }

    private NegotiationStartsByBot loadNegotiationStarts(
            MarketStatsPlanningCalculator.CalendarWindows windows
    ) {
        Map<Long, Integer> todayByBot = new HashMap<>();
        Map<Long, Integer> currentWeekByBot = new HashMap<>();

        for (RealActionAudit audit : realActionAuditRepository
                .findAllByActionTypeAndOutcomeAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(
                        RealActionType.FIRST_OFFER,
                        RealActionAuditOutcome.CONFIRMED,
                        windows.currentWeekStart()
                )) {
            if (audit.getBotId() == null || audit.getCreatedAt() == null) {
                continue;
            }

            LocalDateTime createdAt = audit.getCreatedAt();
            if (createdAt.isBefore(windows.currentWeekStart())
                    || createdAt.isAfter(windows.now())) {
                continue;
            }

            Long botId = audit.getBotId();
            currentWeekByBot.merge(botId, 1, this::safeAdd);

            if (!createdAt.isBefore(windows.todayStart())) {
                todayByBot.merge(botId, 1, this::safeAdd);
            }
        }

        return new NegotiationStartsByBot(
                Map.copyOf(todayByBot),
                Map.copyOf(currentWeekByBot)
        );
    }

    private int sumNegotiationStarts(
            List<Long> botIds,
            Map<Long, Integer> startsByBot
    ) {
        long sum = 0L;

        for (Long botId : botIds) {
            sum += Math.max(startsByBot.getOrDefault(botId, 0), 0);
        }

        return safeInt(sum);
    }

    private int safeAdd(
            int left,
            int right
    ) {
        return safeInt((long) left + right);
    }

    private int countNewListings(
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
                observationRepository.countNewListingsBetween(
                        modelId,
                        fromInclusive,
                        toExclusive
                )
        );
    }

    private int countObservedListings(
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
                observationRepository.countListingsObservedDuringWindow(
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

    private record NegotiationStartsByBot(
            Map<Long, Integer> todayByBot,
            Map<Long, Integer> currentWeekByBot
    ) {
    }
}
