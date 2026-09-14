package pl.flipbot.mapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.flipbot.bot.Bot;
import pl.flipbot.bot.configuration.BotAdditionalTargetRepository;
import pl.flipbot.bot.dto.BotPlaywrightResponse;
import pl.flipbot.bot.dto.BotResponse;
import pl.flipbot.bot.dto.RunningBotResponse;
import pl.flipbot.listing.ListingRepository;
import pl.flipbot.listing.ListingStatus;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class BotMapper {

    private static final Set<ListingStatus> ACTIVE_NEGOTIATION_STATUSES =
            EnumSet.of(ListingStatus.NEGOTIATING, ListingStatus.ACTION_REQUIRED);

    private final BotConfigurationMapper botConfigurationMapper;
    private final BotAdditionalTargetRepository additionalTargetRepository;
    private final BotAdditionalTargetMapper additionalTargetMapper;
    private final ListingRepository listingRepository;

    public BotResponse map(Bot bot) {

        return BotResponse.builder()
                .id(bot.getId())
                .name(bot.getName())
                .email(bot.getEmail())
                .status(bot.getStatus().name())
                .configuration(
                        botConfigurationMapper.map(bot.getConfiguration())
                )
                .additionalTargets(
                        additionalTargetRepository
                                .findAllByConfigurationBotIdAndActiveTrueOrderByIdAsc(bot.getId())
                                .stream()
                                .map(additionalTargetMapper::map)
                                .toList()
                )
                .build();
    }

    public BotPlaywrightResponse mapPlaywright(Bot bot) {
        Set<Long> targetsWithActiveNegotiations = new HashSet<>(
                listingRepository.findDistinctAdditionalTargetIdsByBotIdAndStatusIn(
                        bot.getId(),
                        ACTIVE_NEGOTIATION_STATUSES
                )
        );

        return BotPlaywrightResponse.builder()
                .id(bot.getId())
                .name(bot.getName())
                .email(bot.getEmail())
                .password(bot.getPassword())
                .configuration(
                        botConfigurationMapper.map(bot.getConfiguration())
                )
                .additionalTargets(
                        additionalTargetRepository
                                .findAllByConfigurationBotIdOrderByIdAsc(bot.getId())
                                .stream()
                                .filter(target -> Boolean.TRUE.equals(target.getActive())
                                        || targetsWithActiveNegotiations.contains(target.getId()))
                                .map(additionalTargetMapper::map)
                                .toList()
                )
                .build();

    }

    public RunningBotResponse mapRunning(Bot bot) {

        return mapRunning(
                bot,
                false
        );
    }

    public RunningBotResponse mapRunning(
            Bot bot,
            boolean hasActiveNegotiations
    ) {

        return RunningBotResponse.builder()
                .id(bot.getId())
                .hasActiveNegotiations(hasActiveNegotiations)
                .build();
    }
}
