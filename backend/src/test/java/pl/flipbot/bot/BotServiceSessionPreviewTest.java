package pl.flipbot.bot;

import org.junit.jupiter.api.Test;
import pl.flipbot.bot.configuration.BotConfigurationRepository;
import pl.flipbot.bot.runtime.BotSessionPreviewService;
import pl.flipbot.listing.ListingRepository;
import pl.flipbot.mapper.BotMapper;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BotServiceSessionPreviewTest {

    @Test
    void stopThenImmediateStartClearsOnlyThatBotsPreviewWithoutSchedulerSync() {
        BotRepository repository = mock(BotRepository.class);
        Bot bot = Bot.builder().id(7L).status(BotStatus.RUNNING).build();
        when(repository.findById(7L)).thenReturn(Optional.of(bot));
        BotSessionPreviewService previews = new BotSessionPreviewService();
        previews.setPreviewRequested(7L, true);
        previews.setPreviewRequested(8L, true);
        BotService service = new BotService(
                repository, mock(BotConfigurationRepository.class),
                mock(ListingRepository.class), mock(BotMapper.class), previews
        );

        service.stopBot(7L);
        service.startBot(7L);

        assertEquals(BotStatus.RUNNING, bot.getStatus());
        assertFalse(previews.isPreviewRequested(7L));
        assertTrue(previews.isPreviewRequested(8L));
    }
}
