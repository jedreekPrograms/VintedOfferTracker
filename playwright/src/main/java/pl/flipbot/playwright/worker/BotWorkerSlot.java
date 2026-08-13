package pl.flipbot.playwright.worker;

import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.BotApiClient;
import pl.flipbot.playwright.browser.BrowserManager;
import pl.flipbot.playwright.model.BotDetailsDto;
import pl.flipbot.playwright.target.VintedRateLimitException;

import java.util.concurrent.TimeUnit;

@Slf4j
public class BotWorkerSlot implements Runnable {

    private final int slotNumber;

    private final BotRunScheduler scheduler;

    private final WorkerRuntimeConfig config;

    private final BotApiClient botApiClient =
            new BotApiClient();


    public BotWorkerSlot(
            int slotNumber,
            BotRunScheduler scheduler,
            WorkerRuntimeConfig config
    ) {

        this.slotNumber =
                slotNumber;

        this.scheduler =
                scheduler;

        this.config =
                config;
    }


    @Override
    public void run() {

        log.info(
                "[SLOT {}] Starting reusable worker slot on thread {}.",
                slotNumber,
                Thread.currentThread().getName()
        );


        try (
                BrowserManager browserManager =
                        new BrowserManager()
        ) {

            while (
                    !Thread.currentThread()
                            .isInterrupted()
            ) {

                ScheduledBotTask task =
                        scheduler.takeNext();


                Long botId =
                        task.botId();


                long nextDelayMillis =
                        TimeUnit.SECONDS.toMillis(
                                config.normalRunDelaySeconds()
                        );


                try {

                    log.info(
                            "[SLOT {}] Claimed bot {}. Queue={}, working={}.",
                            slotNumber,
                            botId,
                            scheduler.queuedCount(),
                            scheduler.workingCount()
                    );


                    BotDetailsDto bot =
                            botApiClient.getBot(
                                    botId
                            );


                    ScheduledBotRunExecutor runExecutor =
                            new ScheduledBotRunExecutor(
                                    bot,
                                    browserManager
                            );


                    runExecutor.executeOneRun();


                    log.info(
                            "[SLOT {}] Bot {} completed one scheduled run.",
                            slotNumber,
                            botId
                    );

                } catch (VintedRateLimitException exception) {

                    nextDelayMillis =
                            TimeUnit.SECONDS.toMillis(
                                    config.rateLimitDelaySeconds()
                            );


                    log.warn(
                            "[SLOT {}] Bot {} hit an explicit Vinted rate limit. "
                                    + "Next run delayed by {} seconds.",
                            slotNumber,
                            botId,
                            config.rateLimitDelaySeconds()
                    );


                    log.debug(
                            "[SLOT {}] Rate-limit exception for bot {}.",
                            slotNumber,
                            botId,
                            exception
                    );

                } catch (Exception exception) {

                    nextDelayMillis =
                            TimeUnit.SECONDS.toMillis(
                                    config.failureDelaySeconds()
                            );


                    log.error(
                            "[SLOT {}] Bot {} failed during its scheduled run. "
                                    + "Next attempt in {} seconds.",
                            slotNumber,
                            botId,
                            config.failureDelaySeconds(),
                            exception
                    );

                } finally {

                    scheduler.completeRun(
                            botId,
                            nextDelayMillis
                    );
                }
            }

        } catch (InterruptedException exception) {

            Thread.currentThread()
                    .interrupt();


            log.info(
                    "[SLOT {}] Worker slot interrupted.",
                    slotNumber
            );

        } catch (Exception exception) {

            log.error(
                    "[SLOT {}] Worker slot stopped because its Playwright runtime failed.",
                    slotNumber,
                    exception
            );

        } finally {

            log.info(
                    "[SLOT {}] Worker slot stopped.",
                    slotNumber
            );
        }
    }
}
