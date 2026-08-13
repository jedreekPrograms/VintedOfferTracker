package pl.flipbot.playwright.worker;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.DelayQueue;

@Slf4j
public class BotRunScheduler {

    private enum RunState {
        QUEUED,
        WORKING
    }


    private final DelayQueue<ScheduledBotTask> queue =
            new DelayQueue<>();

    private final Set<Long> enabledBots =
            new HashSet<>();

    private final Map<Long, RunState> stateByBotId =
            new HashMap<>();


    public synchronized void reconcileRunningBots(
            Set<Long> runningBotIds
    ) {

        Set<Long> normalizedRunningBotIds =
                runningBotIds.stream()
                        .filter(botId -> botId != null && botId > 0)
                        .collect(java.util.stream.Collectors.toSet());


        Set<Long> botsToDisable =
                new HashSet<>(
                        enabledBots
                );

        botsToDisable.removeAll(
                normalizedRunningBotIds
        );


        for (
                Long botId
                : botsToDisable
        ) {

            enabledBots.remove(
                    botId
            );


            RunState currentState =
                    stateByBotId.get(
                            botId
                    );


            if (
                    currentState
                            == RunState.QUEUED
            ) {

                queue.removeIf(
                        task -> botId.equals(
                                task.botId()
                        )
                );

                stateByBotId.remove(
                        botId
                );


                log.info(
                        "[SCHEDULER] Removed queued work for stopped bot {}.",
                        botId
                );

            } else if (
                    currentState
                            == RunState.WORKING
            ) {

                log.info(
                        "[SCHEDULER] Bot {} was stopped while WORKING. "
                                + "The current run may finish, but no next run will be scheduled.",
                        botId
                );
            }
        }


        for (
                Long botId
                : normalizedRunningBotIds
        ) {

            boolean newlyEnabled =
                    enabledBots.add(
                            botId
                    );


            if (
                    newlyEnabled
                            && !stateByBotId.containsKey(
                            botId
                    )
            ) {

                queue.offer(
                        ScheduledBotTask.now(
                                botId
                        )
                );

                stateByBotId.put(
                        botId,
                        RunState.QUEUED
                );


                log.info(
                        "[SCHEDULER] Scheduled newly RUNNING bot {} immediately.",
                        botId
                );
            }
        }
    }


    public ScheduledBotTask takeNext()
            throws InterruptedException {

        while (
                true
        ) {

            ScheduledBotTask task =
                    queue.take();


            synchronized (
                    this
            ) {

                Long botId =
                        task.botId();


                if (
                        !enabledBots.contains(
                                botId
                        )
                ) {

                    stateByBotId.remove(
                            botId
                    );

                    continue;
                }


                if (
                        stateByBotId.get(
                                botId
                        ) != RunState.QUEUED
                ) {

                    continue;
                }


                stateByBotId.put(
                        botId,
                        RunState.WORKING
                );


                return task;
            }
        }
    }


    public synchronized void completeRun(
            Long botId,
            long nextDelayMillis
    ) {

        if (
                !enabledBots.contains(
                        botId
                )
        ) {

            stateByBotId.remove(
                    botId
            );


            log.info(
                    "[SCHEDULER] Bot {} finished its current run after being stopped. "
                            + "No next run scheduled.",
                    botId
            );


            return;
        }


        ScheduledBotTask nextTask =
                ScheduledBotTask.afterDelay(
                        botId,
                        nextDelayMillis
                );


        queue.offer(
                nextTask
        );

        stateByBotId.put(
                botId,
                RunState.QUEUED
        );


        log.info(
                "[SCHEDULER] Bot {} queued again in {} ms. Queue size: {}.",
                botId,
                Math.max(0L, nextDelayMillis),
                queue.size()
        );
    }


    public synchronized void shutdown() {

        enabledBots.clear();
        stateByBotId.clear();
        queue.clear();
    }


    public synchronized int queuedCount() {

        return queue.size();
    }


    public synchronized int workingCount() {

        return (int) stateByBotId.values()
                .stream()
                .filter(state -> state == RunState.WORKING)
                .count();
    }


    public synchronized int enabledBotCount() {

        return enabledBots.size();
    }
}
