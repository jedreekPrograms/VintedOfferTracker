package pl.flipbot.marketstats;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.flipbot.bot.configuration.BotAdditionalTarget;
import pl.flipbot.bot.configuration.BotAdditionalTargetRepository;
import pl.flipbot.bot.configuration.BotConfiguration;
import pl.flipbot.bot.configuration.BotConfigurationRepository;
import pl.flipbot.bot.configuration.TargetMode;
import pl.flipbot.analytics.HistoryModelResolver;
import pl.flipbot.listing.Listing;
import pl.flipbot.listing.ListingRepository;
import pl.flipbot.dictionary.DictionaryModel;
import pl.flipbot.dictionary.DictionaryModelRepository;
import pl.flipbot.marketstats.dto.CalendarModelPlanningResponse;
import pl.flipbot.negotiation.audit.RealActionAudit;
import pl.flipbot.negotiation.audit.RealActionAuditOutcome;
import pl.flipbot.negotiation.audit.RealActionAuditRepository;
import pl.flipbot.negotiation.guard.RealActionType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MarketStatsCalendarPlanningService {

    private static final ZoneId MARKET_STATS_ZONE = ZoneId.of("Europe/Warsaw");
    private static final long MIN_CURRENT_WINDOW_FRESHNESS_MINUTES = 120L;
    private static final long MAX_CURRENT_WINDOW_FRESHNESS_MINUTES = 360L;
    private static final long ESTIMATED_MINUTES_PER_MODEL = 2L;
    private static final int CAPACITY_LOOKBACK_DAYS = 28;
    private static final int FALLBACK_DAILY_CONVERSATION_CAPACITY = 5;
    private static final int HARD_DAILY_OFFER_LIMIT = 25;

    private final DictionaryModelRepository modelRepository;
    private final BotConfigurationRepository configurationRepository;
    private final BotAdditionalTargetRepository additionalTargetRepository;
    private final MarketModelScanStateRepository scanStateRepository;
    private final MarketListingObservationRepository observationRepository;
    private final RealActionAuditRepository realActionAuditRepository;
    private final ListingRepository listingRepository;
    private final HistoryModelResolver historyModelResolver;

    @Transactional(readOnly = true)
    public List<CalendarModelPlanningResponse> getPlanning() {
        LocalDateTime now = LocalDateTime.now(MARKET_STATS_ZONE);
        List<BotConfiguration> configurations = configurationRepository.findAll();
        List<BotAdditionalTarget> additionalTargets =
                additionalTargetRepository.findAll()
                        .stream()
                        .filter(target -> Boolean.TRUE.equals(target.getActive()))
                        .toList();
        List<DictionaryModel> models = modelRepository.findAll();

        // One bulk query instead of a repository lookup for every calendar row.
        List<Long> modelIds = models.stream().map(DictionaryModel::getId).toList();
        Map<Long, MarketModelScanState> scanStates = new HashMap<>();
        for (MarketModelScanState state : scanStateRepository.findAllById(modelIds)) {
            scanStates.put(state.getModelId(), state);
        }
        MarketStatsPlanningCalculator.CalendarWindows windows =
                MarketStatsPlanningCalculator.windows(now);
        Map<Long, PublishedWindowCounts> publishedCounts =
                loadPublishedWindowCounts(modelIds, windows);

        ConversationCapacityProfile capacityProfile =
                loadConversationCapacity(
                        now.toLocalDate(),
                        models
                );
        long currentWindowFreshnessMinutes =
                currentWindowFreshnessMinutes(models.size());

        return models.stream()
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
                        additionalTargets,
                        scanStates.get(model.getId()),
                        publishedCounts.getOrDefault(model.getId(), PublishedWindowCounts.ZERO),
                        capacityProfile,
                        now,
                        currentWindowFreshnessMinutes
                ))
                .toList();
    }

    private CalendarModelPlanningResponse toPlanningResponse(
            DictionaryModel model,
            List<BotConfiguration> configurations,
            List<BotAdditionalTarget> additionalTargets,
            MarketModelScanState state,
            PublishedWindowCounts counts,
            ConversationCapacityProfile capacityProfile,
            LocalDateTime now,
            long currentWindowFreshnessMinutes
    ) {
        List<Long> matchingBotIds = matchingBotIds(
                model,
                configurations,
                additionalTargets
        );
        int existingBots = matchingBotIds.size();
        int dailyConversationCapacityPerBot =
                capacityProfile.dailyCapacityFor(
                        model.getId(),
                        matchingBotIds
                );
        int weeklyConversationCapacityPerBot =
                dailyConversationCapacityPerBot
                        * MarketStatsPlanningCalculator.DAYS_PER_WEEK;

        if (state == null || state.getBaselineCompleteAt() == null) {
            return new CalendarModelPlanningResponse(
                    model.getId(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    dailyConversationCapacityPerBot,
                    weeklyConversationCapacityPerBot,
                    true,
                    existingBots,
                    false,
                    false,
                    false,
                    0,
                    state == null ? null : state.getLastScanAt(),
                    state == null ? null : state.getLastSuccessfulScanAt(),
                    false,
                    state != null && Boolean.TRUE.equals(state.getLastScanComplete())
            );
        }

        LocalDateTime baselineCompleteAt = state.getBaselineCompleteAt();
        MarketStatsPlanningCalculator.CalendarWindows windows =
                MarketStatsPlanningCalculator.windows(now);

        int offersToday = counts.today();
        int offersCurrentWeek = counts.currentWeek();

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
                        now.minusMinutes(currentWindowFreshnessMinutes)
                );

        /*
         * A traversal of today's currently-active catalog cannot reconstruct
         * offers which were published and sold before tracking started.
         * Therefore a calendar window is exact only when the model baseline
         * was already complete at the START of that window.
         *
         * Current-day/current-week values are exact through the latest
         * successful complete scan. Freshness is exposed separately so the UI
         * can show "stan na HH:mm" instead of hiding a valid count merely
         * because a large Observer pass takes longer than two hours.
         */
        boolean todayWindowComplete = publicationCoverageEstablished
                && latestScanComplete
                && successfulScanToday
                && MarketStatsPlanningCalculator.coversWindowFrom(
                        baselineCompleteAt,
                        windows.todayStart()
                );
        boolean currentWeekWindowComplete = publicationCoverageEstablished
                && latestScanComplete
                && successfulScanToday
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
            offersPreviousFullWeek = counts.previousWeek();
            recommendationWeeklyOffers = offersPreviousFullWeek;
            recommendationEstimated = false;
        } else {
            int observedSinceBaseline = counts.sinceBaseline();
            recommendationWeeklyOffers =
                    MarketStatsPlanningCalculator.projectWeeklyOffers(
                            observedSinceBaseline,
                            Math.max(trackedDays, 1)
                    );
            recommendationEstimated = true;
        }

        int recommendedBots = MarketStatsPlanningCalculator.recommendedBots(
                recommendationWeeklyOffers,
                weeklyConversationCapacityPerBot
        );

        return new CalendarModelPlanningResponse(
                model.getId(),
                state.getBaselineOfferCount(),
                offersToday,
                offersCurrentWeek,
                offersPreviousFullWeek,
                recommendedBots,
                recommendationWeeklyOffers,
                dailyConversationCapacityPerBot,
                weeklyConversationCapacityPerBot,
                recommendationEstimated,
                existingBots,
                todayWindowComplete,
                currentWeekWindowComplete,
                previousFullWeekAvailable,
                trackedDays,
                state.getLastScanAt(),
                lastSuccessfulScanAt,
                currentScanFresh,
                latestScanComplete
        );
    }

    static long currentWindowFreshnessMinutes(int modelCount) {
        long estimatedCycleMinutes =
                Math.max(0L, modelCount) * ESTIMATED_MINUTES_PER_MODEL;

        return Math.max(
                MIN_CURRENT_WINDOW_FRESHNESS_MINUTES,
                Math.min(
                        MAX_CURRENT_WINDOW_FRESHNESS_MINUTES,
                        estimatedCycleMinutes
                )
        );
    }

    /**
     * One query for all models instead of up to four count queries per model.
     * Models without a finished baseline or matching observations have zero counts.
     */
    private Map<Long, PublishedWindowCounts> loadPublishedWindowCounts(
            List<Long> modelIds,
            MarketStatsPlanningCalculator.CalendarWindows windows
    ) {
        if (modelIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, PublishedWindowCounts> result = new HashMap<>();
        for (Object[] row : observationRepository.countPublishedListingWindows(
                modelIds, windows.todayStart(), windows.currentWeekStart(),
                windows.previousWeekStart(), windows.now()
        )) {
            Long modelId = ((Number) row[0]).longValue();
            result.put(modelId, new PublishedWindowCounts(
                    safeInt(((Number) row[1]).longValue()),
                    safeInt(((Number) row[2]).longValue()),
                    safeInt(((Number) row[3]).longValue()),
                    safeInt(((Number) row[4]).longValue())
            ));
        }
        return result;
    }

    private record PublishedWindowCounts(
            int today, int currentWeek, int previousWeek, int sinceBaseline
    ) {
        static final PublishedWindowCounts ZERO = new PublishedWindowCounts(0, 0, 0, 0);
    }

    private List<Long> matchingBotIds(
            DictionaryModel model,
            List<BotConfiguration> configurations,
            List<BotAdditionalTarget> additionalTargets
    ) {
        Set<Long> ids = new LinkedHashSet<>();

        for (BotConfiguration configuration : configurations) {
            if (matchesModel(model, configuration)
                    && configuration.getBot() != null
                    && configuration.getBot().getId() != null) {
                ids.add(configuration.getBot().getId());
            }
        }

        for (BotAdditionalTarget target : additionalTargets) {
            if (matchesModel(model, target)
                    && target.getConfiguration() != null
                    && target.getConfiguration().getBot() != null
                    && target.getConfiguration().getBot().getId() != null) {
                ids.add(target.getConfiguration().getBot().getId());
            }
        }

        return List.copyOf(ids);
    }

    private ConversationCapacityProfile loadConversationCapacity(
            LocalDate today,
            List<DictionaryModel> models
    ) {
        LocalDateTime from = today
                .minusDays(CAPACITY_LOOKBACK_DAYS - 1L)
                .atStartOfDay();

        List<RealActionAudit> audits = realActionAuditRepository
                .findAllByActionTypeAndOutcomeAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(
                        RealActionType.FIRST_OFFER,
                        RealActionAuditOutcome.CONFIRMED,
                        from
                );

        if (audits.isEmpty()) {
            return ConversationCapacityProfile.empty();
        }

        Set<Long> listingIds = audits.stream()
                .map(RealActionAudit::getBackendListingId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());

        Map<Long, Listing> listingsById = listingRepository
                .findAllById(listingIds)
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        Listing::getId,
                        listing -> listing
                ));

        Map<Long, Map<Long, Map<LocalDate, Integer>>> counts =
                new HashMap<>();

        for (RealActionAudit audit : audits) {
            if (audit.getBotId() == null
                    || audit.getCreatedAt() == null
                    || audit.getBackendListingId() == null) {
                continue;
            }

            Listing listing = listingsById.get(audit.getBackendListingId());
            if (listing == null) {
                continue;
            }

            Long modelId = historyModelResolver
                    .resolveModelId(listing, models)
                    .orElse(null);

            if (modelId == null) {
                continue;
            }

            counts.computeIfAbsent(
                            modelId,
                            ignored -> new HashMap<>()
                    )
                    .computeIfAbsent(
                            audit.getBotId(),
                            ignored -> new HashMap<>()
                    )
                    .merge(
                            audit.getCreatedAt().toLocalDate(),
                            1,
                            Integer::sum
                    );
        }

        Map<Long, Map<Long, List<Integer>>> compact = new HashMap<>();

        for (Map.Entry<Long, Map<Long, Map<LocalDate, Integer>>> modelEntry
                : counts.entrySet()) {
            Map<Long, List<Integer>> botCounts = new HashMap<>();

            for (Map.Entry<Long, Map<LocalDate, Integer>> botEntry
                    : modelEntry.getValue().entrySet()) {
                List<Integer> dailyCounts = botEntry.getValue()
                        .values()
                        .stream()
                        .filter(value -> value != null && value > 0)
                        .sorted()
                        .toList();

                if (!dailyCounts.isEmpty()) {
                    botCounts.put(botEntry.getKey(), dailyCounts);
                }
            }

            if (!botCounts.isEmpty()) {
                compact.put(modelEntry.getKey(), Map.copyOf(botCounts));
            }
        }

        return new ConversationCapacityProfile(Map.copyOf(compact));
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

    private record ConversationCapacityProfile(
            Map<Long, Map<Long, List<Integer>>> dailyCountsByModelAndBot
    ) {
        static ConversationCapacityProfile empty() {
            return new ConversationCapacityProfile(Map.of());
        }

        int dailyCapacityFor(
                Long modelId,
                List<Long> botIds
        ) {
            if (modelId == null || botIds == null || botIds.isEmpty()) {
                return FALLBACK_DAILY_CONVERSATION_CAPACITY;
            }

            Map<Long, List<Integer>> byBot =
                    dailyCountsByModelAndBot.getOrDefault(
                            modelId,
                            Map.of()
                    );

            List<Integer> counts = new ArrayList<>();
            for (Long botId : botIds) {
                counts.addAll(byBot.getOrDefault(botId, List.of()));
            }

            if (counts.isEmpty()) {
                return FALLBACK_DAILY_CONVERSATION_CAPACITY;
            }

            List<Integer> sorted = counts.stream().sorted().toList();
            int index = Math.max(
                    0,
                    (int) Math.ceil(sorted.size() * 0.75d) - 1
            );
            int demonstrated = sorted.get(
                    Math.min(index, sorted.size() - 1)
            );

            return Math.min(
                    HARD_DAILY_OFFER_LIMIT,
                    Math.max(
                            FALLBACK_DAILY_CONVERSATION_CAPACITY,
                            demonstrated
                    )
            );
        }
    }

    private int safeInt(long value) {
        return value > Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : (int) Math.max(value, 0L);
    }
}