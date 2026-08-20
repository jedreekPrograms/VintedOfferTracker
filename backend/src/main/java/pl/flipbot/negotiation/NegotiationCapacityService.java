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

    private final NegotiationPlanner negotiationPlanner;

    private final DailyOfferQuotaService dailyOfferQuotaService;

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

        int allowedNewNegotiations =
                negotiationPlanner.calculateNewNegotiations(
                        bot,
                        quota.remaining()
                );

        return new NegotiationCapacityResponse(
                Math.max(allowedNewNegotiations, 0)
        );
    }
}
