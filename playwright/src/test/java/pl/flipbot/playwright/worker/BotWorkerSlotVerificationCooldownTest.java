package pl.flipbot.playwright.worker;

import org.junit.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import pl.flipbot.playwright.api.BotApiClient;
import pl.flipbot.playwright.api.runtime.RuntimeTelemetryReporter;
import pl.flipbot.playwright.browser.BrowserCapacityController;
import pl.flipbot.playwright.browser.BrowserManager;
import pl.flipbot.playwright.model.BotDetailsDto;
import pl.flipbot.playwright.verification.HumanVerificationRequiredException;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class BotWorkerSlotVerificationCooldownTest {
    @Test
    public void captchaPausesAllOfAffectedBotsJobsWithoutBlockingOtherBotsOrLeakingBrowser() throws Exception {
        WorkerRuntimeConfig config = new WorkerRuntimeConfig(
                1, 5L, 120L, 900L, 60L, 60L, 600L, 180L, 30L, true);
        RuntimeTelemetryReporter telemetry = mock(RuntimeTelemetryReporter.class);
        BotRunScheduler scheduler = spy(new BotRunScheduler(config, telemetry, new CatalogConcurrencyConfig(3, 1000L)));
        BrowserCapacityController capacity = mock(BrowserCapacityController.class);
        BrowserCapacityController.Permit permit = mock(BrowserCapacityController.Permit.class);
        when(capacity.acquire()).thenReturn(permit);
        BotDetailsDto bot = new BotDetailsDto();
        bot.setId(4L);
        AtomicReference<BotWorkerSlot> slot = new AtomicReference<>();

        try (MockedStatic<BrowserCapacityController> shared = mockStatic(BrowserCapacityController.class);
             MockedConstruction<BotApiClient> api = mockConstruction(BotApiClient.class,
                     (client, ignored) -> when(client.getBot(4L)).thenReturn(bot));
             MockedConstruction<BrowserManager> browsers = mockConstruction(BrowserManager.class);
             MockedConstruction<ScheduledBotRunExecutor> jobs = mockConstruction(ScheduledBotRunExecutor.class,
                     (executor, ignored) -> doAnswer(call -> {
                         slot.get().requestRetirement();
                         throw new HumanVerificationRequiredException("Visible CAPTCHA requires manual completion");
                     }).when(executor).executeJob(any()))) {
            shared.when(BrowserCapacityController::shared).thenReturn(capacity);
            scheduler.reconcileRunningBots(Map.of(4L, true));
            slot.set(new BotWorkerSlot(1, scheduler, config, telemetry, new BotSessionPreviewRegistry()));

            slot.get().run();

            verify(scheduler).completeRun(4L, ScheduledJobType.NEGOTIATION_CHECK,
                    HumanVerificationRequiredException.RETRY_DELAY_MILLIS, true, false);
            scheduler.reconcileRunningBots(Map.of(4L, true));
            assertNull("Neither catalog nor negotiation may bypass the CAPTCHA cooldown", scheduler.pollNext(20L));
            verify(telemetry).runFailed(eq(4L), anyLong(), anyLong(), contains("HumanVerificationRequiredException"));
            verify(telemetry, never()).sessionBlocked(anyLong(), anyLong(), anyString());
            verify(telemetry, never()).rateLimited(anyLong(), anyLong(), anyLong(), anyString());
            assertEquals(1, browsers.constructed().size());
            verify(browsers.constructed().getFirst()).close();
            verify(permit).close();
            verify(capacity).marketplaceBackoff(HumanVerificationRequiredException.RETRY_DELAY_MILLIS);

            scheduler.reconcileRunningBots(Map.of(4L, true, 7L, true));
            ScheduledBotTask otherBot = scheduler.pollNext(100L);
            assertNotNull(otherBot);
            assertEquals(Long.valueOf(7L), otherBot.botId());
        } finally {
            scheduler.shutdown();
        }
    }
}
