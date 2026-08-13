package pl.flipbot.bot.scheduler;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.flipbot.bot.Bot;
import pl.flipbot.bot.BotRepository;
import pl.flipbot.bot.BotStatus;
import pl.flipbot.bot.dto.RunningBotResponse;
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

    @Transactional(readOnly = true)
    public List<RunningBotResponse> getRunningBots() {

        List<Bot> runningBots =
                botRepository.findByStatus(
                        BotStatus.RUNNING
                );

        if (runningBots.isEmpty()) {
            return List.of();
        }

        List<Long> runningBotIds =
                runningBots.stream()
                        .map(Bot::getId)
                        .toList();

        Set<Long> botsWithActiveNegotiations =
                new HashSet<>(
                        listingRepository.findDistinctBotIdsByStatusAndBotIdIn(
                                ListingStatus.NEGOTIATING,
                                runningBotIds
                        )
                );

        return runningBots.stream()
                .map(
                        bot -> RunningBotResponse.builder()
                                .id(bot.getId())
                                .hasActiveNegotiations(
                                        botsWithActiveNegotiations.contains(
                                                bot.getId()
                                        )
                                )
                                .build()
                )
                .toList();
    }
}
