package pl.flipbot.mapper;

import org.junit.jupiter.api.Test;
import pl.flipbot.bot.Bot;
import pl.flipbot.bot.configuration.BotAdditionalTarget;
import pl.flipbot.bot.configuration.BotAdditionalTargetRepository;
import pl.flipbot.bot.dto.BotAdditionalTargetResponse;
import pl.flipbot.bot.dto.BotPlaywrightResponse;
import pl.flipbot.listing.ListingRepository;
import pl.flipbot.listing.ListingStatus;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BotMapperTest {

    @Test
    void playwrightPayloadKeepsInactiveProductsOnlyWhileNegotiationsAreActive() {
        BotConfigurationMapper configurationMapper =
                mock(BotConfigurationMapper.class);
        BotAdditionalTargetRepository additionalTargetRepository =
                mock(BotAdditionalTargetRepository.class);
        BotAdditionalTargetMapper additionalTargetMapper =
                mock(BotAdditionalTargetMapper.class);
        ListingRepository listingRepository = mock(ListingRepository.class);

        BotMapper mapper = new BotMapper(
                configurationMapper,
                additionalTargetRepository,
                additionalTargetMapper,
                listingRepository
        );

        Bot bot = Bot.builder()
                .id(9L)
                .name("bot-9")
                .email("bot@example.com")
                .password("encrypted")
                .build();

        BotAdditionalTarget active = BotAdditionalTarget.builder()
                .id(101L)
                .active(true)
                .build();
        BotAdditionalTarget inactiveWithNegotiation = BotAdditionalTarget.builder()
                .id(102L)
                .active(false)
                .build();
        BotAdditionalTarget inactiveHistorical = BotAdditionalTarget.builder()
                .id(103L)
                .active(false)
                .build();

        BotAdditionalTargetResponse activeResponse =
                BotAdditionalTargetResponse.builder()
                        .additionalTargetId(101L)
                        .active(true)
                        .build();
        BotAdditionalTargetResponse inactiveResponse =
                BotAdditionalTargetResponse.builder()
                        .additionalTargetId(102L)
                        .active(false)
                        .build();

        when(additionalTargetRepository
                .findAllByConfigurationBotIdOrderByIdAsc(9L))
                .thenReturn(List.of(
                        active,
                        inactiveWithNegotiation,
                        inactiveHistorical
                ));
        when(listingRepository
                .findDistinctAdditionalTargetIdsByBotIdAndStatusIn(
                        eq(9L),
                        eq(Set.of(
                                ListingStatus.NEGOTIATING,
                                ListingStatus.ACTION_REQUIRED
                        ))
                ))
                .thenReturn(List.of(102L));
        when(additionalTargetMapper.map(active)).thenReturn(activeResponse);
        when(additionalTargetMapper.map(inactiveWithNegotiation))
                .thenReturn(inactiveResponse);

        BotPlaywrightResponse response = mapper.mapPlaywright(bot);

        assertEquals(2, response.getAdditionalTargets().size());
        assertSame(activeResponse, response.getAdditionalTargets().get(0));
        assertSame(inactiveResponse, response.getAdditionalTargets().get(1));
        verify(additionalTargetRepository)
                .findAllByConfigurationBotIdOrderByIdAsc(9L);
        verify(listingRepository)
                .findDistinctAdditionalTargetIdsByBotIdAndStatusIn(
                        eq(9L),
                        eq(Set.of(
                                ListingStatus.NEGOTIATING,
                                ListingStatus.ACTION_REQUIRED
                        ))
                );
    }
}
