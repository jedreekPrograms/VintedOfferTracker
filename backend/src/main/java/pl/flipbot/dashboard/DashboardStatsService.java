package pl.flipbot.dashboard;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.flipbot.bot.BotRepository;
import pl.flipbot.bot.BotStatus;
import pl.flipbot.dashboard.dto.DashboardStatsResponse;
import pl.flipbot.listing.Listing;
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
    public DashboardStatsResponse getStats(
            DashboardPeriod period
    ) {

        List<Listing> listings =
                listingRepository.findAll();


        /*
         * Te trzy wartości pokazują aktualny stan systemu,
         * więc nie filtrujemy ich po czasie.
         */

        long activeBotsCount =
                botRepository.findAll()
                        .stream()
                        .filter(
                                bot ->
                                        bot.getStatus()
                                                == BotStatus.RUNNING
                        )
                        .count();


        long negotiatingCount =
                listings.stream()
                        .filter(
                                listing ->
                                        listing.getStatus()
                                                == ListingStatus.NEGOTIATING
                        )
                        .count();


        long actionRequiredCount =
                listings.stream()
                        .filter(
                                listing ->
                                        listing.getStatus()
                                                == ListingStatus.ACTION_REQUIRED
                        )
                        .count();


        /*
         * Historia decyzji jest filtrowana
         * według wybranego okresu. Wpisy ręcznie usunięte z historii
         * pozostają w bazie jako zabezpieczenie przed ponownym odkryciem
         * tej samej oferty, ale nie wpływają już na statystyki historyczne.
         */

        List<Listing> listingsInPeriod =
                listings.stream()
                        .filter(
                                listing ->
                                        !listing.isHistoryHidden()
                        )
                        .filter(
                                listing ->
                                        isInPeriod(
                                                listing,
                                                period
                                        )
                        )
                        .toList();


        List<Listing> purchasedListings =
                listingsInPeriod.stream()
                        .filter(
                                listing ->
                                        listing.getStatus()
                                                == ListingStatus.PURCHASED
                        )
                        .toList();


        long purchasedCount =
                purchasedListings.size();


        long skippedByUserCount =
                listingsInPeriod.stream()
                        .filter(
                                listing ->
                                        listing.getStatus()
                                                == ListingStatus.SKIPPED_BY_USER
                        )
                        .count();


        BigDecimal totalSpent =
                purchasedListings.stream()
                        .map(
                                Listing::getCurrentPrice
                        )
                        .filter(
                                price ->
                                        price != null
                        )
                        .reduce(
                                BigDecimal.ZERO,
                                BigDecimal::add
                        );


        BigDecimal totalNegotiatedSavings =
                purchasedListings.stream()
                        .map(
                                this::calculateSavings
                        )
                        .reduce(
                                BigDecimal.ZERO,
                                BigDecimal::add
                        );


        BigDecimal averagePurchasePrice =
                calculateAveragePurchasePrice(
                        purchasedListings,
                        totalSpent
                );


        BigDecimal averageDiscountPercentage =
                calculateAverageDiscountPercentage(
                        purchasedListings
                );


        return new DashboardStatsResponse(
                activeBotsCount,
                negotiatingCount,
                actionRequiredCount,
                purchasedCount,
                skippedByUserCount,
                totalSpent,
                totalNegotiatedSavings,
                averagePurchasePrice,
                averageDiscountPercentage
        );
    }


    private boolean isInPeriod(
            Listing listing,
            DashboardPeriod period
    ) {

        if (
                period == DashboardPeriod.ALL
        ) {

            return true;
        }


        LocalDateTime decisionAt =
                listing.getDecisionAt();


        /*
         * DISCOVERED, NEGOTIATING i ACTION_REQUIRED
         * nie mają decisionAt.
         *
         * Przy statystykach historycznych interesują
         * nas PURCHASED i SKIPPED_BY_USER.
         */

        if (
                decisionAt == null
        ) {

            return false;
        }


        LocalDateTime from =
                getPeriodStart(
                        period
                );


        return !decisionAt.isBefore(
                from
        );
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
            Listing listing
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
            List<Listing> purchasedListings,
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
            List<Listing> purchasedListings
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
            Listing listing
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
