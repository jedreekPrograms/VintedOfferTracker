package pl.flipbot.negotiation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.flipbot.bot.Bot;
import pl.flipbot.bot.BotRepository;
import pl.flipbot.exception.BotNotFoundException;
import pl.flipbot.negotiation.dto.NegotiationCapacityResponse;
import pl.flipbot.negotiation.quota.DailyOfferQuotaService;
import pl.flipbot.negotiation.quota.dto.DailyOfferQuotaResponse;

@Service
@RequiredArgsConstructor
public class NegotiationCapacityService {

    private final BotRepository botRepository;

    private final DailyOfferQuotaService dailyOfferQuotaService;

    /**
     * A new negotiation consumes exactly one quota slot now: the first offer.
     * Future steps are NOT pre-reserved because they may happen hours/days
     * later, may never be needed, and are independently quota-checked when
     * they are actually sent.
     *
     * The previous implementation reserved every possible future step for
     * every active negotiation. With five configured steps, six active
     * conversations could therefore consume the whole theoretical budget and
     * block dozens of perfectly valid DISCOVERED listings even when only a
     * handful of real offers had actually been sent that day.
     */
    public NegotiationCapacityResponse calculateCapacity(
            Long botId
    ) {
        Bot bot = botRepository.findById(botId)
                .orElseThrow(
                        () -> new BotNotFoundException(botId)
                );

        if (bot.getConfiguration() == null
                || bot.getConfiguration().getNegotiationSteps() == null
                || bot.getConfiguration().getNegotiationSteps().isEmpty()) {
            return new NegotiationCapacityResponse(0);
        }

        DailyOfferQuotaResponse quota =
                dailyOfferQuotaService.getQuota(botId);

        return new NegotiationCapacityResponse(
                Math.max(quota.remaining(), 0)
        );
    }
}
