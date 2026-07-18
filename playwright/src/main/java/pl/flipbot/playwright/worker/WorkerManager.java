package pl.flipbot.playwright.worker;

import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.BotApiClient;
import pl.flipbot.playwright.browser.BrowserManager;
import pl.flipbot.playwright.model.BotDetailsDto;
import pl.flipbot.playwright.model.RunningBotDto;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
public class WorkerManager {

    private final BotApiClient botApiClient = new BotApiClient();

    private final BrowserManager browserManager;

    private final ScheduledExecutorService executor =
            Executors.newScheduledThreadPool(10);

    private final Map<Long, Future<?>> workers =
            new ConcurrentHashMap<>();

    public WorkerManager(BrowserManager browserManager) {
        this.browserManager = browserManager;
    }

    public void syncWorkers() {

        Set<Long> runningBotIds = botApiClient.getRunningBots()
                .stream()
                .map(RunningBotDto::getId)
                .collect(Collectors.toSet());
    }

    public void start() {

        executor.scheduleWithFixedDelay(
                this::syncWorkers,
                0,
                5,
                TimeUnit.SECONDS
        );
    }

    public void stop() {

        workers.values()
                .forEach(f -> f.cancel(true));

        executor.shutdownNow();

    }

    private void startMissingWorkers(Set<Long> runningBotIds) {

        for (Long botId : runningBotIds) {

            if (workers.containsKey(botId)) {
                continue;
            }

            BotDetailsDto bot = botApiClient.getBot(botId);

            BotWorker worker = new BotWorker(bot, browserManager);

            Future<?> future = executor.submit(worker);

            workers.put(botId, future);

            log.info(
                    "Started worker for bot {}",
                    botId
            );
        }
    }

    private void stopInactiveWorkers(Set<Long> runningBotIds) {

        workers.entrySet()
                .removeIf(entry -> {

                    Long botId = entry.getKey();

                    if (runningBotIds.contains(botId)) {
                        return false;
                    }

                    entry.getValue().cancel(true);

                    log.info(
                            "Stopped worker for bot {}",
                            botId
                    );

                    return true;
                });
    }
}
