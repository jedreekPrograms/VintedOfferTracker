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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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
        List<RealActionAudit> confirmedFirstOffers =
                loadConfirmedFirstOffers(windows);

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
                        confirmedFirstOffers
                ))
                .toList();
    }

    private CalendarModelPlanningResponse toPlanningResponse(
            DictionaryModel model,
            List<BotConfiguration> configurations,
            LocalDateTime now,
            MarketStatsPlanningCalculator.CalendarWindows windows,
            List<RealActionAudit> confirmedFirstOffers
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
        int negotiationsStartedToday = countUniqueConfirmedStarts(
                matchingBotIds,
                confirmedFirstOffers,
                windows.todayStart(),
                windows.now(),
                null
        );
        int negotiationsStartedCurrentWeek = countUniqueConfirmedStarts(
                matchingBotIds,
                confirmedFirstOffers,
                windows.currentWeekStart(),
                windows.now(),
                null
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
        Integer negotiationsStartedPreviousFullWeek = null;
        Double empiricalConversationsPerBotPreviousFullWeek = null;
        Integer recommendedBots = null;
        int recommendationWeeklyOffers;
        boolean recommendationEstimated;

        int trackedDays = MarketStatsPlanningCalculator.trackedCalendarDays(
                baselineCompleteAt,
                now
        );

        if (previousFullWeekAvailable) {
            /*
             * The denominator is the exact set of unique listings that existed
             * at any point during the completed Monday-Sunday window. The
             * numerator below is restricted to that same set of marketplace ids,
             * so "started / opportunities" really describes coverage of those
             * market opportunities rather than two unrelated counters.
             */
            Set<String> previousWeekOpportunityIds =
                    findObservedListingIds(
                            model.getId(),
                            windows.previousWeekStart(),
                            windows.currentWeekStart()
                    );

            offersPreviousFullWeek = previousWeekOpportunityIds.size();
            negotiationsStartedPreviousFullWeek = countUniqueConfirmedStarts(
                    matchingBotIds,
                    confirmedFirstOffers,
                    windows.previousWeekStart(),
                    windows.currentWeekStart(),
                    previousWeekOpportunityIds
            );
            empiricalConversationsPerBotPreviousFullWeek =
                    MarketStatsPlanningCalculator.observedConversationsPerBot(
                            negotiationsStartedPreviousFullWeek,
                            existingBots
                    );
            recommendedBots =
                    MarketStatsPlanningCalculator.recommendedBotsFromObservedThroughput(
                            offersPreviousFullWeek,
                            negotiationsStartedPreviousFullWeek,
                            existingBots
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

        return new CalendarModelPlanningResponse(
                model.getId(),
                state.getBaselineOfferCount(),
                offersToday,
                offersCurrentWeek,
                offersPreviousFullWeek,
                negotiationsStartedToday,
                negotiationsStartedCurrentWeek,
                negotiationsStartedPreviousFullWeek,
                empiricalConversationsPerBotPreviousFullWeek,
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

    private List<RealActionAudit> loadConfirmedFirstOffers(
            MarketStatsPlanningCalculator.CalendarWindows windows
    ) {
        return realActionAuditRepository
                .findAllByActionTypeAndOutcomeAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(
                        RealActionType.FIRST_OFFER,
                        RealActionAuditOutcome.CONFIRMED,
                        windows.previousWeekStart()
                );
    }

    private int countUniqueConfirmedStarts(
            List<Long> botIds,
            List<RealActionAudit> audits,
            LocalDateTime fromInclusive,
            LocalDateTime toExclusive,
            Set<String> allowedMarketplaceListingIds
    ) {
        if (botIds == null
                || botIds.isEmpty()
                || audits == null
                || audits.isEmpty()
                || fromInclusive == null
                || toExclusive == null
                || !fromInclusive.isBefore(toExclusive)) {
            return 0;
        }

        Set<Long> allowedBotIds = new HashSet<>(botIds);
        Set<String> uniqueListingIds = new HashSet<>();

        for (RealActionAudit audit : audits) {
            if (audit == null
                    || audit.getBotId() == null
                    || audit.getCreatedAt() == null
                    || audit.getMarketplaceListingId() == null
                    || audit.getMarketplaceListingId().isBlank()
                    || audit.getActionType() != RealActionType.FIRST_OFFER
                    || audit.getOutcome() != RealActionAuditOutcome.CONFIRMED
                    || !allowedBotIds.contains(audit.getBotId())) {
                continue;
            }

            LocalDateTime createdAt = audit.getCreatedAt();
            if (createdAt.isBefore(fromInclusive)
                    || !createdAt.isBefore(toExclusive)) {
                continue;
            }

            String marketplaceListingId = audit.getMarketplaceListingId();
            if (allowedMarketplaceListingIds != null
                    && !allowedMarketplaceListingIds.contains(marketplaceListingId)) {
                continue;
            }

            uniqueListingIds.add(marketplaceListingId);
        }

        return safeInt(uniqueListingIds.size());
    }

    private Set<String> findObservedListingIds(
            Long modelId,
            LocalDateTime fromInclusive,
            LocalDateTime toExclusive
    ) {
        if (fromInclusive == null
                || toExclusive == null
                || !fromInclusive.isBefore(toExclusive)) {
            return Set.of();
        }

        return new HashSet<>(
                observationRepository.findListingIdsObservedDuringWindow(
                        modelId,
                        fromInclusive,
                        toExclusive
                )
        );
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
