package pl.flipbot.bot.scheduler;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.flipbot.bot.Bot;
import pl.flipbot.bot.BotRepository;
import pl.flipbot.bot.BotStatus;
import pl.flipbot.bot.dto.RunningBotResponse;
import pl.flipbot.bot.runtime.BotRuntimeState;
import pl.flipbot.bot.runtime.BotRuntimeStateRepository;
import pl.flipbot.listing.ListingRepository;
import pl.flipbot.listing.ListingStatus;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SchedulerRunningBotService {

    private final BotRepository botRepository;
    private final ListingRepository listingRepository;
    private final BotRuntimeStateRepository runtimeStateRepository;

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

        Map<Long, BotRuntimeState> runtimeByBotId =
                runtimeStateRepository.findAllById(runningBotIds)
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        BotRuntimeState::getBotId,
                                        Function.identity()
                                )
                        );

        return runningBots.stream()
                .map(
                        bot -> {
                            BotRuntimeState runtime =
                                    runtimeByBotId.get(bot.getId());
                            boolean captchaRequired = runtime != null
                                    && runtime.getCaptchaRequiredSince() != null;

                            return RunningBotResponse.builder()
                                    .id(bot.getId())
                                    .hasActiveNegotiations(
                                            botsWithActiveNegotiations.contains(
                                                    bot.getId()
                                            )
                                    )
                                    .captchaRequired(captchaRequired)
                                    .captchaRecoveryRequested(
                                            captchaRequired
                                                    && runtime.getCaptchaRecoveryRequestedAt() != null
                                    )
                                    .captchaChallengeUrl(
                                            captchaRequired
                                                    ? runtime.getCaptchaChallengeUrl()
                                                    : null
                                    )
                                    .build();
                        }
                )
                .toList();
    }
}
