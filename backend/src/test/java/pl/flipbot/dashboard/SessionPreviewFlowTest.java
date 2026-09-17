package pl.flipbot.dashboard;

import org.junit.jupiter.api.Test;
import pl.flipbot.bot.Bot;
import pl.flipbot.bot.BotRepository;
import pl.flipbot.bot.BotStatus;
import pl.flipbot.bot.runtime.BotRuntimeStateRepository;
import pl.flipbot.bot.runtime.BotSessionPreviewService;
import pl.flipbot.bot.scheduler.SchedulerRunningBotService;
import pl.flipbot.listing.ListingRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SessionPreviewFlowTest {

    @Test
    void showAndHideReachBothTheDashboardAndTheExistingWorkerSyncForOnlyTheSelectedBot() {
        BotRepository bots = mock(BotRepository.class);
        var running = List.of(
                Bot.builder().id(7L).name("First").status(BotStatus.RUNNING).build(),
                Bot.builder().id(8L).name("Second").status(BotStatus.RUNNING).build()
        );
        when(bots.findRuntimeBots()).thenReturn(running.stream().map(bot -> {
            var row = mock(BotRepository.RuntimeBot.class);
            when(row.getId()).thenReturn(bot.getId());
            when(row.getName()).thenReturn(bot.getName());
            when(row.getStatus()).thenReturn(bot.getStatus());
            return row;
        }).toList());
        when(bots.findIdsByStatus(BotStatus.RUNNING)).thenReturn(List.of(7L, 8L));
        BotSessionPreviewService previews = new BotSessionPreviewService();
        RuntimeDashboardService runtime = new RuntimeDashboardService(
                bots, mock(BotRuntimeStateRepository.class), previews);
        DashboardController controller = new DashboardController(
                mock(DashboardStatsService.class), runtime, previews);
        SchedulerRunningBotService sync = new SchedulerRunningBotService(
                bots, mock(ListingRepository.class), previews);

        assertFalse(runtime.getRuntimeDashboard().bots().getFirst().sessionPreviewRequested());
        assertEquals(204, controller.setSessionPreview(7L, true).getStatusCode().value());
        var displayed = controller.getRuntimeDashboard().getBody();
        assertNotNull(displayed);
        assertTrue(displayed.bots().stream().filter(bot -> bot.botId() == 7L)
                .findFirst().orElseThrow().sessionPreviewRequested());
        assertFalse(displayed.bots().stream().filter(bot -> bot.botId() == 8L)
                .findFirst().orElseThrow().sessionPreviewRequested());
        assertTrue(sync.getRunningBots().stream().filter(bot -> bot.getId() == 7L)
                .findFirst().orElseThrow().isSessionPreviewRequested());
        assertFalse(sync.getRunningBots().stream().filter(bot -> bot.getId() == 8L)
                .findFirst().orElseThrow().isSessionPreviewRequested());

        controller.setSessionPreview(7L, false);
        assertTrue(runtime.getRuntimeDashboard().bots().stream()
                .noneMatch(bot -> bot.sessionPreviewRequested()));
        assertTrue(sync.getRunningBots().stream()
                .noneMatch(bot -> bot.isSessionPreviewRequested()));
    }
}
