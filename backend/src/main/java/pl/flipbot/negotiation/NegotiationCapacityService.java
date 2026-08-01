package pl.flipbot.negotiation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.flipbot.bot.Bot;
import pl.flipbot.bot.BotRepository;
import pl.flipbot.exception.BotNotFoundException;
import pl.flipbot.negotiation.dto.NegotiationCapacityResponse;

@Service
@RequiredArgsConstructor
public class NegotiationCapacityService {

    private final BotRepository botRepository;

    private final NegotiationPlanner negotiationPlanner;

    public NegotiationCapacityResponse calculateCapacity(
            Long botId
    ) {

        Bot bot =
                botRepository.findById(
                                botId
                        )
                        .orElseThrow(
                                () -> new BotNotFoundException(
                                        botId
                                )
                        );

        int allowedNewNegotiations =
                negotiationPlanner
                        .calculateNewNegotiations(
                                bot
                        );

        return new NegotiationCapacityResponse(
                allowedNewNegotiations
        );

    }

}