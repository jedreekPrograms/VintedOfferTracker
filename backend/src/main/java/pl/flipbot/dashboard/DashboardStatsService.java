package pl.flipbot.dashboard;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.flipbot.bot.BotRepository;
import pl.flipbot.bot.BotStatus;
import pl.flipbot.dashboard.dto.DashboardStatsResponse;
import pl.flipbot.listing.ListingRepository.PurchaseAmounts;
import pl.flipbot.listing.ListingRepository;
import pl.flipbot.listing.ListingStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardStatsService {

    private final ListingRepository listingRepository;

    private final BotRepository botRepository;


    @Transactional(readOnly = true)
    public DashboardStatsResponse getStats(DashboardPeriod period) {
        // Counts stay in SQL. Money uses lightweight scalar rows so the exact
        // existing BigDecimal/rounding rules remain unchanged.
        var currentCounts = listingRepository.countByListingStatus();
        boolean allTime = period == DashboardPeriod.ALL;
        LocalDateTime from = allTime ? LocalDate.now().atStartOfDay() : getPeriodStart(period);
        var historyCounts = listingRepository.countVisibleHistory(
                List.of(ListingStatus.PURCHASED, ListingStatus.SKIPPED_BY_USER), allTime, from);
        List<PurchaseAmounts> purchasedListings = listingRepository.findPurchaseAmounts(
                ListingStatus.PURCHASED, allTime, from);
        BigDecimal totalSpent = purchasedListings.stream().map(PurchaseAmounts::getCurrentPrice)
                .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal savings = purchasedListings.stream().map(this::calculateSavings)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new DashboardStatsResponse(
                botRepository.countByStatusAndMarketStatsObserverFalse(BotStatus.RUNNING),
                count(currentCounts, ListingStatus.NEGOTIATING),
                count(currentCounts, ListingStatus.ACTION_REQUIRED),
                count(historyCounts, ListingStatus.PURCHASED),
                count(historyCounts, ListingStatus.SKIPPED_BY_USER),
                totalSpent, savings, calculateAveragePurchasePrice(purchasedListings, totalSpent),
                calculateAverageDiscountPercentage(purchasedListings));
    }

    private long count(List<ListingRepository.StatusCount> rows, ListingStatus status) {
        return rows.stream().filter(row -> row.getStatus() == status)
                .mapToLong(ListingRepository.StatusCount::getTotal).sum();
    }

    private LocalDateTime getPeriodStart(
            DashboardPeriod period
    ) {

        LocalDate today =
                LocalDate.now();


        return switch (period) {

            case TODAY ->
                    today.atStartOfDay();

            case LAST_7_DAYS ->
                    today
                            .minusDays(
                                    6
                            )
                            .atStartOfDay();

            case LAST_30_DAYS ->
                    today
                            .minusDays(
                                    29
                            )
                            .atStartOfDay();

            case ALL ->
                    LocalDateTime.MIN;
        };
    }


    private BigDecimal calculateSavings(
            PurchaseAmounts listing
    ) {

        if (
                listing.getOriginalPrice() == null
                        || listing.getCurrentPrice() == null
        ) {

            return BigDecimal.ZERO;
        }


        BigDecimal savings =
                listing.getOriginalPrice()
                        .subtract(
                                listing.getCurrentPrice()
                        );


        if (
                savings.compareTo(
                        BigDecimal.ZERO
                ) < 0
        ) {

            return BigDecimal.ZERO;
        }


        return savings;
    }


    private BigDecimal calculateAveragePurchasePrice(
            List<PurchaseAmounts> purchasedListings,
            BigDecimal totalSpent
    ) {

        long listingsWithPrice =
                purchasedListings.stream()
                        .filter(
                                listing ->
                                        listing.getCurrentPrice()
                                                != null
                        )
                        .count();


        if (
                listingsWithPrice == 0
        ) {

            return BigDecimal.ZERO;
        }


        return totalSpent.divide(
                BigDecimal.valueOf(
                        listingsWithPrice
                ),
                2,
                RoundingMode.HALF_UP
        );
    }


    private BigDecimal calculateAverageDiscountPercentage(
            List<PurchaseAmounts> purchasedListings
    ) {

        List<BigDecimal> discounts =
                purchasedListings.stream()
                        .filter(
                                listing ->
                                        listing.getOriginalPrice() != null
                                                && listing.getCurrentPrice() != null
                                                && listing.getOriginalPrice()
                                                .compareTo(
                                                        BigDecimal.ZERO
                                                ) > 0
                        )
                        .map(
                                this::calculateDiscountPercentage
                        )
                        .toList();


        if (
                discounts.isEmpty()
        ) {

            return BigDecimal.ZERO;
        }


        BigDecimal sum =
                discounts.stream()
                        .reduce(
                                BigDecimal.ZERO,
                                BigDecimal::add
                        );


        return sum.divide(
                BigDecimal.valueOf(
                        discounts.size()
                ),
                2,
                RoundingMode.HALF_UP
        );
    }


    private BigDecimal calculateDiscountPercentage(
            PurchaseAmounts listing
    ) {

        BigDecimal originalPrice =
                listing.getOriginalPrice();

        BigDecimal currentPrice =
                listing.getCurrentPrice();


        BigDecimal difference =
                originalPrice.subtract(
                        currentPrice
                );


        if (
                difference.compareTo(
                        BigDecimal.ZERO
                ) <= 0
        ) {

            return BigDecimal.ZERO;
        }


        return difference
                .divide(
                        originalPrice,
                        6,
                        RoundingMode.HALF_UP
                )
                .multiply(
                        BigDecimal.valueOf(
                                100
                        )
                )
                .setScale(
                        2,
                        RoundingMode.HALF_UP
                );
    }
}
