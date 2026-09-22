package pl.flipbot.analytics;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.flipbot.analytics.dto.AnalyticsOverviewResponse;
import pl.flipbot.dashboard.DashboardPeriod;
import pl.flipbot.dictionary.DictionaryModel;
import pl.flipbot.dictionary.DictionaryModelRepository;
import pl.flipbot.listing.HistoryOutcome;
import pl.flipbot.listing.Listing;
import pl.flipbot.listing.ListingHistoryMetadata;
import pl.flipbot.listing.ListingRepository;
import pl.flipbot.listing.MissedOpportunityReason;
import pl.flipbot.listing.OfferAssessment;
import pl.flipbot.marketstats.MarketListingObservation;
import pl.flipbot.marketstats.MarketListingObservationRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private static final DateTimeFormatter DAY_LABEL =
            DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter MONTH_LABEL =
            DateTimeFormatter.ofPattern("MM.yyyy");

    private final ListingRepository listingRepository;
    private final MarketListingObservationRepository observationRepository;
    private final DictionaryModelRepository modelRepository;

    /**
     * Compatibility overload for older callers.
     */
    @Transactional(readOnly = true)
    public AnalyticsOverviewResponse getOverview(
            DashboardPeriod period,
            Long modelId,
            Set<HistoryOutcome> outcomes,
            Set<OfferAssessment> assessments,
            AnalyticsSource source
    ) {
        return getOverview(
                period,
                modelId == null ? Set.of() : Set.of(modelId),
                outcomes,
                assessments,
                source,
                AnalyticsGranularity.DAY,
                null,
                null
        );
    }

    @Transactional(readOnly = true)
    public AnalyticsOverviewResponse getOverview(
            DashboardPeriod period,
            Set<Long> modelIds,
            Set<HistoryOutcome> outcomes,
            Set<OfferAssessment> assessments,
            AnalyticsSource source,
            AnalyticsGranularity granularity,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        DashboardPeriod effectivePeriod = period == null
                ? DashboardPeriod.ALL
                : period;
        AnalyticsSource effectiveSource = source == null
                ? AnalyticsSource.ALL
                : source;
        AnalyticsGranularity effectiveGranularity = granularity == null
                ? AnalyticsGranularity.DAY
                : granularity;
        Set<Long> selectedModelIds = modelIds == null
                ? Set.of()
                : Set.copyOf(modelIds);

        LocalDateTime now = LocalDateTime.now();
        TimeRange range = resolveRange(
                effectivePeriod,
                fromDate,
                toDate,
                now
        );

        List<DictionaryModel> models = modelRepository.findAll()
                .stream()
                .sorted(
                        Comparator.comparing(
                                        (DictionaryModel model) ->
                                                model.getBrand().getName(),
                                        String.CASE_INSENSITIVE_ORDER
                                )
                                .thenComparing(
                                        DictionaryModel::getName,
                                        String.CASE_INSENSITIVE_ORDER
                                )
                )
                .toList();

        Map<Long, String> modelLabels = models.stream()
                .collect(
                        Collectors.toMap(
                                DictionaryModel::getId,
                                this::label,
                                (left, right) -> left,
                                LinkedHashMap::new
                        )
                );

        Set<Long> unknownModelIds = selectedModelIds.stream()
                .filter(id -> !modelLabels.containsKey(id))
                .collect(Collectors.toSet());

        if (!unknownModelIds.isEmpty()) {
            throw new java.util.NoSuchElementException(
                    "Dictionary model(s) were not found: " + unknownModelIds
            );
        }

        Set<String> selectedModelLabels = selectedModelIds.stream()
                .map(modelLabels::get)
                .map(ListingHistoryMetadata::normalizeLabel)
                .collect(Collectors.toSet());

        List<Listing> history = effectiveSource == AnalyticsSource.OBSERVER
                ? List.of()
                : listingRepository.findAll()
                        .stream()
                        .filter(ListingHistoryMetadata::isHistoryListing)
                        .filter(listing -> !listing.isHistoryHidden())
                        .filter(listing -> within(
                                ListingHistoryMetadata.effectiveHistoryDate(listing),
                                range
                        ))
                        .filter(listing ->
                                selectedModelLabels.isEmpty()
                                        || selectedModelLabels.contains(
                                        ListingHistoryMetadata.normalizeLabel(
                                                ListingHistoryMetadata.effectiveModelLabel(listing)
                                        )
                                )
                        )
                        .filter(listing ->
                                outcomes == null
                                        || outcomes.isEmpty()
                                        || matchesOutcomeFilter(
                                                listing,
                                                outcomes
                                        )
                        )
                        .filter(listing ->
                                assessments == null
                                        || assessments.isEmpty()
                                        || assessments.contains(
                                        ListingHistoryMetadata.effectiveAssessment(listing)
                                )
                        )
                        .toList();

        List<MarketListingObservation> market =
                effectiveSource == AnalyticsSource.HISTORY
                        ? List.of()
                        : observationRepository.findAll()
                                .stream()
                                .filter(observation ->
                                        observation.getPublishedAt() != null
                                )
                                .filter(observation ->
                                        within(
                                                observation.getPublishedAt(),
                                                range
                                        )
                                )
                                .filter(observation ->
                                        selectedModelIds.isEmpty()
                                                || observation.getModel() != null
                                                && selectedModelIds.contains(
                                                observation.getModel().getId()
                                        )
                                )
                                .toList();

        AnalyticsOverviewResponse.Summary summary =
                buildSummary(history, market, range);

        List<DictionaryModel> modelsForBreakdown = selectedModelIds.isEmpty()
                ? models
                : models.stream()
                        .filter(model -> selectedModelIds.contains(model.getId()))
                        .toList();

        List<AnalyticsOverviewResponse.ModelBreakdown> breakdowns =
                modelsForBreakdown.stream()
                        .map(model -> buildModelBreakdown(
                                model,
                                history,
                                market,
                                range
                        ))
                        .filter(this::hasData)
                        .toList();

        List<AnalyticsOverviewResponse.TimelinePoint> timeline =
                buildTimeline(
                        history,
                        market,
                        effectiveGranularity
                );

        List<BigDecimal> marketPrices =
                market.stream()
                        .map(this::marketPrice)
                        .filter(this::positive)
                        .toList();

        List<AnalyticsOverviewResponse.HistogramBucket> histogram =
                AnalyticsMath.histogram(marketPrices, 10)
                        .stream()
                        .map(bucket ->
                                new AnalyticsOverviewResponse.HistogramBucket(
                                        bucket.from(),
                                        bucket.to(),
                                        bucket.count()
                                )
                        )
                        .toList();

        List<AnalyticsOverviewResponse.ModelOption> modelOptions =
                models.stream()
                        .map(model ->
                                new AnalyticsOverviewResponse.ModelOption(
                                        model.getId(),
                                        model.getBrand().getName(),
                                        model.getName(),
                                        label(model)
                                )
                        )
                        .toList();

        return new AnalyticsOverviewResponse(
                modelOptions,
                summary,
                breakdowns,
                timeline,
                histogram
        );
    }

    private AnalyticsOverviewResponse.Summary buildSummary(
            List<Listing> history,
            List<MarketListingObservation> market,
            TimeRange range
    ) {
        List<BigDecimal> marketPrices = market.stream()
                .map(this::marketPrice)
                .filter(this::positive)
                .toList();

        List<BigDecimal> purchasePrices = history.stream()
                .filter(this::isPurchased)
                .map(Listing::getCurrentPrice)
                .filter(this::positive)
                .toList();

        List<BigDecimal> legitRejectedPrices = history.stream()
                .filter(this::isNotPurchased)
                .filter(listing ->
                        ListingHistoryMetadata.effectiveAssessment(listing)
                                == OfferAssessment.LEGIT
                )
                .map(Listing::getCurrentPrice)
                .filter(this::positive)
                .toList();

        List<BigDecimal> missedPrices = history.stream()
                .filter(this::isMissedOpportunity)
                .map(Listing::getCurrentPrice)
                .filter(this::positive)
                .toList();

        AnalyticsMath.PriceSummary marketSummary =
                AnalyticsMath.summarize(marketPrices);
        AnalyticsMath.PriceSummary purchaseSummary =
                AnalyticsMath.summarize(purchasePrices);
        AnalyticsMath.PriceSummary rejectedSummary =
                AnalyticsMath.summarize(legitRejectedPrices);
        AnalyticsMath.PriceSummary missedSummary =
                AnalyticsMath.summarize(missedPrices);
        MarketRates rates = marketRates(market, range);

        PriceAdvantage advantage = priceAdvantage(
                marketSummary.median(),
                purchaseSummary.median()
        );

        return new AnalyticsOverviewResponse.Summary(
                history.size(),
                history.stream().filter(this::isPurchased).count(),
                history.stream().filter(this::isNotPurchased).count(),
                history.stream().filter(this::isMissedOpportunity).count(),
                countAssessment(history, OfferAssessment.LEGIT),
                countAssessment(history, OfferAssessment.SCAM),
                countAssessment(history, OfferAssessment.UNASSESSED),
                market.size(),
                marketSummary.count(),
                marketSummary.average(),
                marketSummary.median(),
                marketSummary.p25(),
                marketSummary.p75(),
                marketSummary.standardDeviation(),
                marketSummary.min(),
                marketSummary.max(),
                rates.perDay(),
                rates.perWeek(),
                rates.perMonth(),
                purchaseSummary.average(),
                purchaseSummary.median(),
                rejectedSummary.average(),
                rejectedSummary.median(),
                missedSummary.average(),
                missedSummary.median(),
                advantage.amount(),
                advantage.percent()
        );
    }

    private AnalyticsOverviewResponse.ModelBreakdown buildModelBreakdown(
            DictionaryModel model,
            List<Listing> history,
            List<MarketListingObservation> market,
            TimeRange range
    ) {
        String modelLabel = label(model);

        List<Listing> modelHistory = history.stream()
                .filter(listing ->
                        sameLabel(
                                ListingHistoryMetadata.effectiveModelLabel(listing),
                                modelLabel
                        )
                )
                .toList();

        List<MarketListingObservation> modelMarket = market.stream()
                .filter(observation ->
                        observation.getModel() != null
                                && model.getId().equals(
                                observation.getModel().getId()
                        )
                )
                .toList();

        AnalyticsMath.PriceSummary marketSummary =
                AnalyticsMath.summarize(
                        modelMarket.stream()
                                .map(this::marketPrice)
                                .filter(this::positive)
                                .toList()
                );

        AnalyticsMath.PriceSummary purchaseSummary =
                AnalyticsMath.summarize(
                        modelHistory.stream()
                                .filter(this::isPurchased)
                                .map(Listing::getCurrentPrice)
                                .filter(this::positive)
                                .toList()
                );

        PriceAdvantage advantage = priceAdvantage(
                marketSummary.median(),
                purchaseSummary.median()
        );
        MarketRates rates = marketRates(modelMarket, range);

        long legitRejected = modelHistory.stream()
                .filter(this::isNotPurchased)
                .filter(listing ->
                        ListingHistoryMetadata.effectiveAssessment(listing)
                                == OfferAssessment.LEGIT
                )
                .count();

        return new AnalyticsOverviewResponse.ModelBreakdown(
                model.getId(),
                model.getBrand().getName(),
                model.getName(),
                modelLabel,
                modelMarket.size(),
                marketSummary.count(),
                marketSummary.average(),
                marketSummary.median(),
                rates.perDay(),
                rates.perWeek(),
                rates.perMonth(),
                modelHistory.stream().filter(this::isPurchased).count(),
                purchaseSummary.average(),
                purchaseSummary.median(),
                legitRejected,
                modelHistory.stream().filter(this::isMissedOpportunity).count(),
                countAssessment(modelHistory, OfferAssessment.SCAM),
                advantage.amount(),
                advantage.percent()
        );
    }

    private boolean hasData(
            AnalyticsOverviewResponse.ModelBreakdown breakdown
    ) {
        return breakdown.marketListingCount() > 0
                || breakdown.purchasedCount() > 0
                || breakdown.legitRejectedCount() > 0
                || breakdown.missedOpportunityCount() > 0
                || breakdown.scamCount() > 0;
    }

    private List<AnalyticsOverviewResponse.TimelinePoint> buildTimeline(
            List<Listing> history,
            List<MarketListingObservation> market,
            AnalyticsGranularity granularity
    ) {
        Map<LocalDate, List<BigDecimal>> marketPrices = new LinkedHashMap<>();
        Map<LocalDate, Long> marketCounts = new LinkedHashMap<>();
        Map<LocalDate, List<BigDecimal>> purchasePrices = new LinkedHashMap<>();

        for (MarketListingObservation observation : market) {
            LocalDate bucket = bucketStart(
                    observation.getPublishedAt().toLocalDate(),
                    granularity
            );
            marketCounts.merge(bucket, 1L, Long::sum);

            BigDecimal price = marketPrice(observation);
            if (positive(price)) {
                marketPrices.computeIfAbsent(
                        bucket,
                        ignored -> new ArrayList<>()
                ).add(price);
            }
        }

        for (Listing listing : history) {
            if (!isPurchased(listing)) {
                continue;
            }

            LocalDateTime eventAt =
                    ListingHistoryMetadata.effectiveHistoryDate(listing);

            if (eventAt == null || !positive(listing.getCurrentPrice())) {
                continue;
            }

            LocalDate bucket = bucketStart(
                    eventAt.toLocalDate(),
                    granularity
            );
            purchasePrices.computeIfAbsent(
                    bucket,
                    ignored -> new ArrayList<>()
            ).add(listing.getCurrentPrice());
        }

        Set<LocalDate> dates = new TreeSet<>();
        dates.addAll(marketCounts.keySet());
        dates.addAll(purchasePrices.keySet());

        return dates.stream()
                .map(date -> {
                    AnalyticsMath.PriceSummary marketSummary =
                            AnalyticsMath.summarize(
                                    marketPrices.getOrDefault(
                                            date,
                                            List.of()
                                    )
                            );
                    AnalyticsMath.PriceSummary purchaseSummary =
                            AnalyticsMath.summarize(
                                    purchasePrices.getOrDefault(
                                            date,
                                            List.of()
                                    )
                            );

                    return new AnalyticsOverviewResponse.TimelinePoint(
                            date,
                            bucketLabel(date, granularity),
                            marketCounts.getOrDefault(date, 0L),
                            marketSummary.average(),
                            marketSummary.median(),
                            purchaseSummary.count(),
                            purchaseSummary.average(),
                            purchaseSummary.median()
                    );
                })
                .toList();
    }

    private LocalDate bucketStart(
            LocalDate date,
            AnalyticsGranularity granularity
    ) {
        return switch (granularity) {
            case DAY -> date;
            case WEEK -> date.with(
                    TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)
            );
            case MONTH -> date.withDayOfMonth(1);
            case YEAR -> date.withDayOfYear(1);
        };
    }

    private String bucketLabel(
            LocalDate date,
            AnalyticsGranularity granularity
    ) {
        return switch (granularity) {
            case DAY -> date.format(DAY_LABEL);
            case WEEK -> "Tydz. " + date.format(DAY_LABEL);
            case MONTH -> date.format(MONTH_LABEL);
            case YEAR -> Integer.toString(date.getYear());
        };
    }

    private MarketRates marketRates(
            List<MarketListingObservation> market,
            TimeRange requestedRange
    ) {
        if (market.isEmpty()) {
            return MarketRates.empty();
        }

        LocalDate start;
        if (requestedRange.from().equals(LocalDateTime.MIN)) {
            start = market.stream()
                    .map(MarketListingObservation::getPublishedAt)
                    .filter(java.util.Objects::nonNull)
                    .map(LocalDateTime::toLocalDate)
                    .min(LocalDate::compareTo)
                    .orElse(requestedRange.to().toLocalDate());
        } else {
            start = requestedRange.from().toLocalDate();
        }

        LocalDate end = requestedRange.to().toLocalDate();
        if (end.isBefore(start)) {
            return MarketRates.empty();
        }

        long days = Math.max(
                1,
                ChronoUnit.DAYS.between(start, end) + 1
        );
        long weeks = Math.max(1, (days + 6) / 7);
        long months = Math.max(
                1,
                ChronoUnit.MONTHS.between(
                        YearMonth.from(start),
                        YearMonth.from(end)
                ) + 1
        );

        BigDecimal count = BigDecimal.valueOf(market.size());

        return new MarketRates(
                divide(count, days),
                divide(count, weeks),
                divide(count, months)
        );
    }

    private BigDecimal divide(
            BigDecimal value,
            long divisor
    ) {
        return value.divide(
                BigDecimal.valueOf(Math.max(divisor, 1)),
                2,
                RoundingMode.HALF_UP
        );
    }

    private boolean matchesOutcomeFilter(
            Listing listing,
            Set<HistoryOutcome> outcomes
    ) {
        HistoryOutcome effective =
                ListingHistoryMetadata.effectiveOutcome(listing);

        if (outcomes.contains(effective)) {
            return true;
        }

        return outcomes.contains(HistoryOutcome.REJECTED)
                && effective == HistoryOutcome.MISSED_OPPORTUNITY;
    }

    private boolean isPurchased(Listing listing) {
        return ListingHistoryMetadata.effectiveOutcome(listing)
                == HistoryOutcome.PURCHASED;
    }

    private boolean isNotPurchased(Listing listing) {
        HistoryOutcome outcome =
                ListingHistoryMetadata.effectiveOutcome(listing);

        return outcome == HistoryOutcome.REJECTED
                || outcome == HistoryOutcome.MISSED_OPPORTUNITY;
    }

    private boolean isMissedOpportunity(Listing listing) {
        if (ListingHistoryMetadata.effectiveOutcome(listing)
                == HistoryOutcome.MISSED_OPPORTUNITY) {
            return true;
        }

        if (!isNotPurchased(listing)) {
            return false;
        }

        MissedOpportunityReason reason = listing.getMissedOpportunityReason();
        return reason == MissedOpportunityReason.SOLD_BEFORE_PURCHASE
                || reason == MissedOpportunityReason.TOO_SLOW;
    }

    private long countAssessment(
            List<Listing> history,
            OfferAssessment assessment
    ) {
        return history.stream()
                .filter(listing ->
                        ListingHistoryMetadata.effectiveAssessment(listing)
                                == assessment
                )
                .count();
    }

    private BigDecimal marketPrice(MarketListingObservation observation) {
        if (observation == null) {
            return null;
        }

        if (positive(observation.getFirstSeenPrice())) {
            return observation.getFirstSeenPrice();
        }

        return observation.getLatestPrice();
    }

    private boolean within(
            LocalDateTime value,
            TimeRange range
    ) {
        if (value == null) {
            return false;
        }

        return !value.isBefore(range.from())
                && !value.isAfter(range.to());
    }

    private TimeRange resolveRange(
            DashboardPeriod period,
            LocalDate fromDate,
            LocalDate toDate,
            LocalDateTime now
    ) {
        LocalDateTime from = fromDate == null
                ? periodStart(period)
                : fromDate.atStartOfDay();
        LocalDateTime to = toDate == null
                ? now
                : toDate.plusDays(1).atStartOfDay().minusNanos(1);

        if (from.isAfter(to)) {
            throw new IllegalArgumentException(
                    "Analytics 'from' date cannot be after 'to' date."
            );
        }

        return new TimeRange(from, to);
    }

    private LocalDateTime periodStart(DashboardPeriod period) {
        LocalDate today = LocalDate.now();

        return switch (period) {
            case TODAY -> today.atStartOfDay();
            case LAST_7_DAYS -> today.minusDays(6).atStartOfDay();
            case LAST_30_DAYS -> today.minusDays(29).atStartOfDay();
            case THIS_MONTH -> today.withDayOfMonth(1).atStartOfDay();
            case THIS_YEAR -> today.withDayOfYear(1).atStartOfDay();
            case ALL -> LocalDateTime.MIN;
        };
    }

    private String label(DictionaryModel model) {
        return ListingHistoryMetadata.modelLabel(
                model.getBrand().getName(),
                model.getName()
        );
    }

    private boolean sameLabel(
            String left,
            String right
    ) {
        String normalizedLeft =
                ListingHistoryMetadata.normalizeLabel(left);
        String normalizedRight =
                ListingHistoryMetadata.normalizeLabel(right);

        return normalizedLeft != null
                && normalizedRight != null
                && normalizedLeft.equals(normalizedRight);
    }

    private PriceAdvantage priceAdvantage(
            BigDecimal marketMedian,
            BigDecimal purchaseMedian
    ) {
        if (!positive(marketMedian) || !positive(purchaseMedian)) {
            return PriceAdvantage.empty();
        }

        BigDecimal amount = marketMedian
                .subtract(purchaseMedian)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal percent = amount
                .divide(
                        marketMedian,
                        6,
                        RoundingMode.HALF_UP
                )
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);

        return new PriceAdvantage(amount, percent);
    }

    private boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private record TimeRange(
            LocalDateTime from,
            LocalDateTime to
    ) {
    }

    private record MarketRates(
            BigDecimal perDay,
            BigDecimal perWeek,
            BigDecimal perMonth
    ) {
        static MarketRates empty() {
            return new MarketRates(null, null, null);
        }
    }

    private record PriceAdvantage(
            BigDecimal amount,
            BigDecimal percent
    ) {
        static PriceAdvantage empty() {
            return new PriceAdvantage(null, null);
        }
    }
}
