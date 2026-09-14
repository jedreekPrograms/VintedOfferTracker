package pl.flipbot.mapper;

import org.junit.jupiter.api.Test;
import pl.flipbot.bot.Bot;
import pl.flipbot.bot.configuration.BotAdditionalTarget;
import pl.flipbot.bot.configuration.BotAdditionalTargetRepository;
import pl.flipbot.bot.dto.BotAdditionalTargetResponse;
import pl.flipbot.bot.dto.BotPlaywrightResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BotMapperTest {

    @Test
    void playwrightPayloadIncludesInactiveProductsForRestartedNegotiations() {
        BotConfigurationMapper configurationMapper =
                mock(BotConfigurationMapper.class);
        BotAdditionalTargetRepository additionalTargetRepository =
                mock(BotAdditionalTargetRepository.class);
        BotAdditionalTargetMapper additionalTargetMapper =
                mock(BotAdditionalTargetMapper.class);

        BotMapper mapper = new BotMapper(
                configurationMapper,
                additionalTargetRepository,
                additionalTargetMapper
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
        BotAdditionalTarget inactive = BotAdditionalTarget.builder()
                .id(102L)
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
                .thenReturn(List.of(active, inactive));
        when(additionalTargetMapper.map(active)).thenReturn(activeResponse);
        when(additionalTargetMapper.map(inactive)).thenReturn(inactiveResponse);

        BotPlaywrightResponse response = mapper.mapPlaywright(bot);

        assertEquals(2, response.getAdditionalTargets().size());
        assertSame(activeResponse, response.getAdditionalTargets().get(0));
        assertSame(inactiveResponse, response.getAdditionalTargets().get(1));
        verify(additionalTargetRepository)
                .findAllByConfigurationBotIdOrderByIdAsc(9L);
    }
}
