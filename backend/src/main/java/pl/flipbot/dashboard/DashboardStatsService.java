package pl.flipbot.dashboard;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.flipbot.analytics.AnalyticsMath;
import pl.flipbot.bot.BotRepository;
import pl.flipbot.bot.BotStatus;
import pl.flipbot.dashboard.dto.DashboardStatsResponse;
import pl.flipbot.listing.HistoryOutcome;
import pl.flipbot.listing.Listing;
import pl.flipbot.listing.ListingHistoryMetadata;
import pl.flipbot.listing.ListingRepository;
import pl.flipbot.listing.ListingStatus;
import pl.flipbot.listing.OfferAssessment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DashboardStatsService {

    private final ListingRepository listingRepository;
    private final BotRepository botRepository;

    @Transactional(readOnly = true)
    public DashboardStatsResponse getStats(DashboardPeriod period) {
        return getStats(period, Set.of(), Set.of());
    }

    @Transactional(readOnly = true)
    public DashboardStatsResponse getStats(
            DashboardPeriod period,
            Set<HistoryOutcome> outcomes,
            Set<OfferAssessment> assessments
    ) {
        DashboardPeriod effectivePeriod = period == null
                ? DashboardPeriod.ALL
                : period;

        List<Listing> listings = listingRepository.findAll();

        long activeBotsCount = botRepository.findAll()
                .stream()
                .filter(bot -> bot.getStatus() == BotStatus.RUNNING)
                .count();

        long negotiatingCount = listings.stream()
                .filter(listing -> listing.getStatus() == ListingStatus.NEGOTIATING)
                .count();

        long actionRequiredCount = listings.stream()
                .filter(listing -> listing.getStatus() == ListingStatus.ACTION_REQUIRED)
                .count();

        List<Listing> selectedHistory = listings.stream()
                .filter(listing -> !listing.isHistoryHidden())
                .filter(ListingHistoryMetadata::isHistoryListing)
                .filter(listing -> isInPeriod(listing, effectivePeriod))
                .filter(listing -> matchesOutcomeFilter(listing, outcomes))
                .filter(listing -> matchesAssessmentFilter(listing, assessments))
                .toList();

        List<Listing> purchasedListings = selectedHistory.stream()
                .filter(listing ->
                        ListingHistoryMetadata.effectiveOutcome(listing)
                                == HistoryOutcome.PURCHASED
                )
                .toList();

        long purchasedCount = purchasedListings.size();
        long rejectedCount = selectedHistory.stream()
                .filter(listing ->
                        ListingHistoryMetadata.effectiveOutcome(listing)
                                == HistoryOutcome.REJECTED
                )
                .count();
        long missedOpportunityCount = selectedHistory.stream()
                .filter(listing ->
                        ListingHistoryMetadata.effectiveOutcome(listing)
                                == HistoryOutcome.MISSED_OPPORTUNITY
                )
                .count();
        long legitCount = selectedHistory.stream()
                .filter(listing ->
                        ListingHistoryMetadata.effectiveAssessment(listing)
                                == OfferAssessment.LEGIT
                )
                .count();
        long scamCount = selectedHistory.stream()
                .filter(listing ->
                        ListingHistoryMetadata.effectiveAssessment(listing)
                                == OfferAssessment.SCAM
                )
                .count();
        long unassessedCount = selectedHistory.stream()
                .filter(listing ->
                        ListingHistoryMetadata.effectiveAssessment(listing)
                                == OfferAssessment.UNASSESSED
                )
                .count();

        BigDecimal totalSpent = purchasedListings.stream()
                .map(Listing::getCurrentPrice)
                .filter(this::positive)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalNegotiatedSavings = purchasedListings.stream()
                .map(this::calculateSavings)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal averagePurchasePrice =
                calculateAveragePurchasePrice(
                        purchasedListings,
                        totalSpent
                );

        BigDecimal averageDiscountPercentage =
                calculateAverageDiscountPercentage(purchasedListings);

        List<BigDecimal> selectedPrices = selectedHistory.stream()
                .map(Listing::getCurrentPrice)
                .filter(this::positive)
                .toList();

        AnalyticsMath.PriceSummary selectedPriceSummary =
                AnalyticsMath.summarize(selectedPrices);

        return new DashboardStatsResponse(
                activeBotsCount,
                negotiatingCount,
                actionRequiredCount,
                purchasedCount,
                rejectedCount,
                totalSpent,
                totalNegotiatedSavings,
                averagePurchasePrice,
                averageDiscountPercentage,
                selectedHistory.size(),
                missedOpportunityCount,
                legitCount,
                scamCount,
                unassessedCount,
                selectedPriceSummary.average(),
                selectedPriceSummary.median()
        );
    }

    private boolean matchesOutcomeFilter(
            Listing listing,
            Set<HistoryOutcome> outcomes
    ) {
        return outcomes == null
                || outcomes.isEmpty()
                || outcomes.contains(
                        ListingHistoryMetadata.effectiveOutcome(listing)
                );
    }

    private boolean matchesAssessmentFilter(
            Listing listing,
            Set<OfferAssessment> assessments
    ) {
        return assessments == null
                || assessments.isEmpty()
                || assessments.contains(
                        ListingHistoryMetadata.effectiveAssessment(listing)
                );
    }

    private boolean isInPeriod(
            Listing listing,
            DashboardPeriod period
    ) {
        if (period == DashboardPeriod.ALL) {
            return true;
        }

        LocalDateTime eventAt =
                ListingHistoryMetadata.effectiveHistoryDate(listing);

        if (eventAt == null) {
            return false;
        }

        return !eventAt.isBefore(getPeriodStart(period));
    }

    private LocalDateTime getPeriodStart(DashboardPeriod period) {
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

    private BigDecimal calculateSavings(Listing listing) {
        if (!positive(listing.getOriginalPrice())
                || !positive(listing.getCurrentPrice())) {
            return BigDecimal.ZERO;
        }

        BigDecimal savings =
                listing.getOriginalPrice()
                        .subtract(listing.getCurrentPrice());

        return savings.signum() > 0
                ? savings
                : BigDecimal.ZERO;
    }

    private BigDecimal calculateAveragePurchasePrice(
            List<Listing> purchasedListings,
            BigDecimal totalSpent
    ) {
        long listingsWithPrice = purchasedListings.stream()
                .map(Listing::getCurrentPrice)
                .filter(this::positive)
                .count();

        if (listingsWithPrice == 0) {
            return BigDecimal.ZERO;
        }

        return totalSpent.divide(
                BigDecimal.valueOf(listingsWithPrice),
                2,
                RoundingMode.HALF_UP
        );
    }

    private BigDecimal calculateAverageDiscountPercentage(
            List<Listing> purchasedListings
    ) {
        List<BigDecimal> discounts = purchasedListings.stream()
                .filter(listing ->
                        positive(listing.getOriginalPrice())
                                && positive(listing.getCurrentPrice())
                )
                .map(this::calculateDiscountPercentage)
                .toList();

        if (discounts.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal sum = discounts.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return sum.divide(
                BigDecimal.valueOf(discounts.size()),
                2,
                RoundingMode.HALF_UP
        );
    }

    private BigDecimal calculateDiscountPercentage(Listing listing) {
        BigDecimal difference =
                listing.getOriginalPrice()
                        .subtract(listing.getCurrentPrice());

        if (difference.signum() <= 0) {
            return BigDecimal.ZERO;
        }

        return difference
                .divide(
                        listing.getOriginalPrice(),
                        6,
                        RoundingMode.HALF_UP
                )
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }
}
