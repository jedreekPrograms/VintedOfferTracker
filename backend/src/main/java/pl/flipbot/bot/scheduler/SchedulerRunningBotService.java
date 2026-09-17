package pl.flipbot.bot.scheduler;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.flipbot.bot.BotRepository;
import pl.flipbot.bot.BotStatus;
import pl.flipbot.bot.dto.RunningBotResponse;
import pl.flipbot.bot.runtime.BotSessionPreviewService;
import pl.flipbot.listing.ListingRepository;
import pl.flipbot.listing.ListingStatus;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SchedulerRunningBotService {

    private final BotRepository botRepository;
    private final ListingRepository listingRepository;
    private final BotSessionPreviewService sessionPreviewService;

    @Transactional(readOnly = true)
    public List<RunningBotResponse> getRunningBots() {

        List<Long> runningBotIds =
                botRepository.findIdsByStatus(
                        BotStatus.RUNNING
                );

        if (runningBotIds.isEmpty()) {
            sessionPreviewService.retainRunningBots(List.of());
            return List.of();
        }

        /*
         * Preview is deliberately process-local. A stopped bot must never keep
         * a latent headed-browser request that unexpectedly comes back after a
         * later START.
         */
        sessionPreviewService.retainRunningBots(runningBotIds);

        Set<Long> botsWithActiveNegotiations =
                new HashSet<>(
                        listingRepository.findDistinctBotIdsByStatusAndBotIdIn(
                                ListingStatus.NEGOTIATING,
                                runningBotIds
                        )
                );

        return runningBotIds.stream()
                .map(
                        botId -> RunningBotResponse.builder()
                                .id(botId)
                                .hasActiveNegotiations(
                                        botsWithActiveNegotiations.contains(
                                                botId
                                        )
                                )
                                .sessionPreviewRequested(
                                        sessionPreviewService.isPreviewRequested(
                                                botId
                                        )
                                )
                                .build()
                )
                .toList();
    }
}
