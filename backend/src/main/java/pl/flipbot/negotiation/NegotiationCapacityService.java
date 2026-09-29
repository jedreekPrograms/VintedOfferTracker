package pl.flipbot.negotiation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.flipbot.bot.Bot;
import pl.flipbot.bot.BotRepository;
import pl.flipbot.bot.configuration.BotAdditionalTarget;
import pl.flipbot.bot.configuration.BotAdditionalTargetRepository;
import pl.flipbot.exception.BotNotFoundException;
import pl.flipbot.negotiation.dto.NegotiationCapacityResponse;
import pl.flipbot.negotiation.quota.DailyOfferQuotaService;
import pl.flipbot.negotiation.quota.dto.DailyOfferQuotaResponse;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NegotiationCapacityService {

    private final BotRepository botRepository;
    private final BotAdditionalTargetRepository additionalTargetRepository;
    private final NegotiationPlanner negotiationPlanner;
    private final DailyOfferQuotaService dailyOfferQuotaService;

    public NegotiationCapacityResponse calculateCapacity(Long botId) {
        return calculateCapacity(botId, null);
    }

    public NegotiationCapacityResponse calculateCapacity(
            Long botId,
            Long additionalTargetId
    ) {
        Bot bot = botRepository.findById(botId)
                .orElseThrow(() -> new BotNotFoundException(botId));

        List<NegotiationStep> requestedSteps = resolveRequestedSteps(
                bot,
                additionalTargetId
        );

        if (requestedSteps.isEmpty()) {
            log.info(
                    "[NEGOTIATION CAPACITY] Bot {} product {} has no active configured negotiation ladder. allowedNewNegotiations=0.",
                    botId,
                    additionalTargetId == null ? "MAIN" : additionalTargetId
            );
            return new NegotiationCapacityResponse(0);
        }

        DailyOfferQuotaResponse quota = dailyOfferQuotaService.getQuota(botId);

        log.info(
                "[NEGOTIATION CAPACITY] Bot {} product {} shared daily quota: limit={}, usedToday={}, remainingToday={}.",
                botId,
                additionalTargetId == null ? "MAIN" : additionalTargetId,
                quota.limit(),
                quota.used(),
                quota.remaining()
        );

        int allowedNewNegotiations = negotiationPlanner.calculateNewNegotiations(
                bot,
                quota,
                requestedSteps
        );

        return new NegotiationCapacityResponse(
                Math.max(allowedNewNegotiations, 0)
        );
    }

    private List<NegotiationStep> resolveRequestedSteps(
            Bot bot,
            Long additionalTargetId
    ) {
        if (additionalTargetId == null) {
            return bot.getConfiguration() == null
                    || bot.getConfiguration().getNegotiationSteps() == null
                    ? List.of()
                    : bot.getConfiguration().getNegotiationSteps();
        }

        BotAdditionalTarget target = additionalTargetRepository
                .findByIdAndConfigurationBotId(additionalTargetId, bot.getId())
                .orElseThrow(() -> new java.util.NoSuchElementException(
                        "Nie znaleziono dodatkowego produktu "
                                + additionalTargetId
                                + " dla bota "
                                + bot.getId()
                                + "."
                ));

        if (!Boolean.TRUE.equals(target.getActive())) {
            return List.of();
        }

        return target.getNegotiationSteps() == null
                ? List.of()
                : target.getNegotiationSteps();
    }
}
