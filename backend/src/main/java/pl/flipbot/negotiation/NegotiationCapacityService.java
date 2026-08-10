package pl.flipbot.negotiation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.flipbot.bot.Bot;
import pl.flipbot.bot.BotRepository;
import pl.flipbot.exception.BotNotFoundException;
import pl.flipbot.listing.Listing;
import pl.flipbot.listing.ListingRepository;
import pl.flipbot.listing.ListingStatus;
import pl.flipbot.negotiation.dto.NegotiationCapacityResponse;
import pl.flipbot.negotiation.quota.DailyOfferQuotaService;
import pl.flipbot.negotiation.quota.dto.DailyOfferQuotaResponse;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NegotiationCapacityService {

    private final BotRepository botRepository;

    private final ListingRepository listingRepository;

    private final NegotiationPlanner negotiationPlanner;

    private final DailyOfferQuotaService dailyOfferQuotaService;


    public NegotiationCapacityResponse calculateCapacity(
            Long botId
    ) {

        Bot bot =
                botRepository.findById(botId)
                        .orElseThrow(
                                () -> new BotNotFoundException(botId)
                        );


        int plannerCapacity =
                negotiationPlanner.calculateNewNegotiations(
                        bot
                );


        int maxSteps =
                bot.getConfiguration()
                        .getNegotiationSteps()
                        .size();


        if (maxSteps <= 0) {

            return new NegotiationCapacityResponse(
                    0
            );
        }


        DailyOfferQuotaResponse quota =
                dailyOfferQuotaService.getQuota(
                        botId
                );


        List<Listing> activeListings =
                listingRepository
                        .findByBotIdAndStatusOrderByIdAsc(
                                botId,
                                ListingStatus.NEGOTIATING
                        );


        int reservedForActiveNegotiations =
                0;


        for (
                Listing listing
                : activeListings
        ) {

            int currentStep =
                    listing.getCurrentStep() == null
                            ? 0
                            : listing.getCurrentStep();


            int futureSteps =
                    maxSteps
                            - currentStep;


            reservedForActiveNegotiations +=
                    Math.max(
                            futureSteps,
                            0
                    );
        }


        int quotaAvailableForNewNegotiations =
                quota.remaining()
                        - reservedForActiveNegotiations;


        if (
                quotaAvailableForNewNegotiations <= 0
        ) {

            return new NegotiationCapacityResponse(
                    0
            );
        }


        int quotaCapacity =
                quotaAvailableForNewNegotiations
                        / maxSteps;


        int allowedNewNegotiations =
                Math.min(
                        plannerCapacity,
                        quotaCapacity
                );


        return new NegotiationCapacityResponse(
                Math.max(
                        allowedNewNegotiations,
                        0
                )
        );
    }
}