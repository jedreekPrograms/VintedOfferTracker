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
import pl.flipbot.listing.OfferAssessment;
import pl.flipbot.marketstats.MarketListingObservation;
import pl.flipbot.marketstats.MarketListingObservationRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final ListingRepository listingRepository;
    private final MarketListingObservationRepository observationRepository;
    private final DictionaryModelRepository modelRepository;

    @Transactional(readOnly = true)
    public AnalyticsOverviewResponse getOverview(
            DashboardPeriod period,
            Long modelId,
            Set<HistoryOutcome> outcomes,
            Set<OfferAssessment> assessments,
            AnalyticsSource source
    ) {
        DashboardPeriod effectivePeriod = period == null
                ? DashboardPeriod.ALL
                : period;
        AnalyticsSource effectiveSource = source == null
                ? AnalyticsSource.ALL
                : source;

        LocalDateTime from = periodStart(effectivePeriod);
        LocalDateTime to = LocalDateTime.now();

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

        String selectedModelLabel = modelId == null
                ? null
                : modelLabels.get(modelId);

        List<Listing> history = effectiveSource == AnalyticsSource.OBSERVER
                ? List.of()
                : listingRepository.findAll()
                        .stream()
                        .filter(ListingHistoryMetadata::isHistoryListing)
                        .filter(listing -> !listing.isHistoryHidden())
                        .filter(listing -> within(
                                ListingHistoryMetadata.effectiveHistoryDate(listing),
                                from,
                                to,
                                effectivePeriod
                        ))
                        .filter(listing ->
                                selectedModelLabel == null
                                        || sameLabel(
                                        ListingHistoryMetadata.effectiveModelLabel(listing),
                                        selectedModelLabel
                                )
                        )
                        .filter(listing ->
                                outcomes == null
                                        || outcomes.isEmpty()
                                        || outcomes.contains(
                                        ListingHistoryMetadata.effectiveOutcome(listing)
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
                                                from,
                                                to,
                                                effectivePeriod
                                        )
                                )
                                .filter(observation ->
                                        modelId == null
                                                || observation.getModel() != null
                                                && modelId.equals(
                                                observation.getModel().getId()
                                        )
                                )
                                .toList();

        AnalyticsOverviewResponse.Summary summary =
                buildSummary(history, market);

        List<AnalyticsOverviewResponse.ModelBreakdown> breakdowns =
                modelId == null
                        ? models.stream()
                        .map(model -> buildModelBreakdown(
                                model,
                                history,
                                market
                        ))
                        .filter(this::hasData)
                        .toList()
                        : models.stream()
                        .filter(model -> model.getId().equals(modelId))
                        .map(model -> buildModelBreakdown(
                                model,
                                history,
                                market
                        ))
                        .toList();

        List<AnalyticsOverviewResponse.TimelinePoint> timeline =
                buildTimeline(history, market);

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
            List<MarketListingObservation> market
    ) {
        List<BigDecimal> marketPrices = market.stream()
                .map(this::marketPrice)
                .filter(this::positive)
                .toList();

        List<BigDecimal> purchasePrices = history.stream()
                .filter(listing ->
                        ListingHistoryMetadata.effectiveOutcome(listing)
                                == HistoryOutcome.PURCHASED
                )
                .map(Listing::getCurrentPrice)
                .filter(this::positive)
                .toList();

        List<BigDecimal> legitRejectedPrices = history.stream()
                .filter(listing ->
                        ListingHistoryMetadata.effectiveOutcome(listing)
                                == HistoryOutcome.REJECTED
                )
                .filter(listing ->
                        ListingHistoryMetadata.effectiveAssessment(listing)
                                == OfferAssessment.LEGIT
                )
                .map(Listing::getCurrentPrice)
                .filter(this::positive)
                .toList();

        List<BigDecimal> missedPrices = history.stream()
                .filter(listing ->
                        ListingHistoryMetadata.effectiveOutcome(listing)
                                == HistoryOutcome.MISSED_OPPORTUNITY
                )
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

        PriceAdvantage advantage = priceAdvantage(
                marketSummary.median(),
                purchaseSummary.median()
        );

        return new AnalyticsOverviewResponse.Summary(
                history.size(),
                countOutcome(history, HistoryOutcome.PURCHASED),
                countOutcome(history, HistoryOutcome.REJECTED),
                countOutcome(history, HistoryOutcome.MISSED_OPPORTUNITY),
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
            List<MarketListingObservation> market
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
                                .filter(listing ->
                                        ListingHistoryMetadata.effectiveOutcome(listing)
                                                == HistoryOutcome.PURCHASED
                                )
                                .map(Listing::getCurrentPrice)
                                .filter(this::positive)
                                .toList()
                );

        PriceAdvantage advantage = priceAdvantage(
                marketSummary.median(),
                purchaseSummary.median()
        );

        long legitRejected = modelHistory.stream()
                .filter(listing ->
                        ListingHistoryMetadata.effectiveOutcome(listing)
                                == HistoryOutcome.REJECTED
                )
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
                countOutcome(modelHistory, HistoryOutcome.PURCHASED),
                purchaseSummary.average(),
                purchaseSummary.median(),
                legitRejected,
                countOutcome(
                        modelHistory,
                        HistoryOutcome.MISSED_OPPORTUNITY
                ),
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
            List<MarketListingObservation> market
    ) {
        Map<LocalDate, List<BigDecimal>> marketByDay = new LinkedHashMap<>();
        Map<LocalDate, Long> marketCountByDay = new LinkedHashMap<>();
        Map<LocalDate, List<BigDecimal>> purchaseByDay = new LinkedHashMap<>();

        for (MarketListingObservation observation : market) {
            LocalDate date = observation.getPublishedAt().toLocalDate();
            marketCountByDay.merge(date, 1L, Long::sum);

            BigDecimal price = marketPrice(observation);
            if (positive(price)) {
                marketByDay.computeIfAbsent(
                        date,
                        ignored -> new ArrayList<>()
                ).add(price);
            }
        }

        for (Listing listing : history) {
            if (ListingHistoryMetadata.effectiveOutcome(listing)
                    != HistoryOutcome.PURCHASED) {
                continue;
            }

            LocalDateTime eventAt =
                    ListingHistoryMetadata.effectiveHistoryDate(listing);

            if (eventAt == null || !positive(listing.getCurrentPrice())) {
                continue;
            }

            purchaseByDay.computeIfAbsent(
                    eventAt.toLocalDate(),
                    ignored -> new ArrayList<>()
            ).add(listing.getCurrentPrice());
        }

        Set<LocalDate> dates = new java.util.TreeSet<>();
        dates.addAll(marketCountByDay.keySet());
        dates.addAll(purchaseByDay.keySet());

        return dates.stream()
                .map(date -> {
                    AnalyticsMath.PriceSummary marketSummary =
                            AnalyticsMath.summarize(
                                    marketByDay.getOrDefault(
                                            date,
                                            List.of()
                                    )
                            );
                    AnalyticsMath.PriceSummary purchaseSummary =
                            AnalyticsMath.summarize(
                                    purchaseByDay.getOrDefault(
                                            date,
                                            List.of()
                                    )
                            );

                    return new AnalyticsOverviewResponse.TimelinePoint(
                            date,
                            marketCountByDay.getOrDefault(date, 0L),
                            marketSummary.average(),
                            marketSummary.median(),
                            purchaseSummary.count(),
                            purchaseSummary.average()
                    );
                })
                .toList();
    }

    private long countOutcome(
            List<Listing> history,
            HistoryOutcome outcome
    ) {
        return history.stream()
                .filter(listing ->
                        ListingHistoryMetadata.effectiveOutcome(listing)
                                == outcome
                )
                .count();
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
            LocalDateTime from,
            LocalDateTime to,
            DashboardPeriod period
    ) {
        if (value == null) {
            return false;
        }

        if (period == DashboardPeriod.ALL) {
            return !value.isAfter(to);
        }

        return !value.isBefore(from) && !value.isAfter(to);
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

    private record PriceAdvantage(
            BigDecimal amount,
            BigDecimal percent
    ) {
        static PriceAdvantage empty() {
            return new PriceAdvantage(null, null);
        }
    }
}
