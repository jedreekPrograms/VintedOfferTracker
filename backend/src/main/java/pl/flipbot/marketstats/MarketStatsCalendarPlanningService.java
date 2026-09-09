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
import pl.flipbot.negotiation.audit.RealActionMessageStatus;
import pl.flipbot.negotiation.guard.RealActionType;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
        MarketStatsPlanningCalculator.CalendarWindows windows =
                MarketStatsPlanningCalculator.windows(now);
        List<BotConfiguration> configurations = configurationRepository.findAll();
        List<RealActionAudit> previousWeekNegotiations =
                loadConfirmedPreviousWeekNegotiations(windows);

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
                        previousWeekNegotiations,
                        windows
                ))
                .toList();
    }

    private CalendarModelPlanningResponse toPlanningResponse(
            DictionaryModel model,
            List<BotConfiguration> configurations,
            List<RealActionAudit> previousWeekNegotiations,
            MarketStatsPlanningCalculator.CalendarWindows windows
    ) {
        MarketModelScanState state = scanStateRepository
                .findById(model.getId())
                .orElse(null);

        Set<Long> matchingBotIds = configurations.stream()
                .filter(configuration -> matchesModel(model, configuration))
                .map(BotConfiguration::getBot)
                .filter(Objects::nonNull)
                .map(bot -> bot.getId())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        int existingBots = safeInt(matchingBotIds.size());
        int negotiationsStartedPreviousFullWeek =
                countStartedNegotiations(
                        matchingBotIds,
                        previousWeekNegotiations
                );

        if (state == null || state.getBaselineCompleteAt() == null) {
            return new CalendarModelPlanningResponse(
                    model.getId(),
                    null,
                    null,
                    null,
                    null,
                    negotiationsStartedPreviousFullWeek,
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
                windows.now()
        );

        if (previousFullWeekAvailable) {
            offersPreviousFullWeek = countNewListings(
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
                negotiationsStartedPreviousFullWeek,
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

    private List<RealActionAudit> loadConfirmedPreviousWeekNegotiations(
            MarketStatsPlanningCalculator.CalendarWindows windows
    ) {
        return realActionAuditRepository
                .findAllByActionTypeAndOutcomeAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(
                        RealActionType.FIRST_OFFER,
                        RealActionAuditOutcome.CONFIRMED,
                        windows.previousWeekStart()
                )
                .stream()
                .filter(audit -> audit.getCreatedAt() != null)
                .filter(audit -> audit.getCreatedAt().isBefore(windows.currentWeekStart()))
                .filter(audit -> audit.getMessageStatus() == RealActionMessageStatus.CONFIRMED)
                .toList();
    }

    private int countStartedNegotiations(
            Set<Long> matchingBotIds,
            List<RealActionAudit> previousWeekNegotiations
    ) {
        if (matchingBotIds.isEmpty() || previousWeekNegotiations.isEmpty()) {
            return 0;
        }

        return safeInt(
                previousWeekNegotiations.stream()
                        .filter(audit -> matchingBotIds.contains(audit.getBotId()))
                        .map(RealActionAudit::getMarketplaceListingId)
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(listingId -> !listingId.isEmpty())
                        .distinct()
                        .count()
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
