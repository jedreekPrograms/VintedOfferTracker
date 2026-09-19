package pl.flipbot.playwright.worker;

import com.microsoft.playwright.Page;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.BotApiClient;
import pl.flipbot.playwright.browser.BrowserManager;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.marketplace.MarketplaceNavigator;
import pl.flipbot.playwright.model.BotDetailsDto;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Owns dedicated, visible, read-only session previews.
 *
 * <p>A preview is intentionally NOT a scheduled bot job. The scheduler pauses
 * the bot before this manager starts a preview and remains paused until the
 * preview runtime has closed. This gives one clear session owner at a time and
 * prevents a normal headless job from racing a human-visible browser.</p>
 *
 * <p>The preview restores the production storage-state file through
 * {@link BotContext#readOnlySessionClone}. It never saves browser state back to
 * bot-X.json, never clears cookies/storage and never performs an interactive
 * login. If the stored session is stale, the visible preview simply shows what
 * Vinted returns for that stored state while the production checkpoint remains
 * untouched.</p>
 */
@Slf4j
final class SessionPreviewManager implements AutoCloseable {

    private static final long PREVIEW_POLL_MS = 250L;
    private static final long STOP_TIMEOUT_SECONDS = 8L;

    private final BotApiClient botApiClient = new BotApiClient();

    private final ExecutorService executor =
            Executors.newCachedThreadPool(
                    namedThreadFactory("flipbot-session-preview-")
            );

    private final Map<Long, PreviewHandle> handles =
            new ConcurrentHashMap<>();

    void stopUnrequested(Set<Long> requestedBotIds) {
        Set<Long> requested = normalize(requestedBotIds);

        for (Map.Entry<Long, PreviewHandle> entry
                : Set.copyOf(handles.entrySet())) {

            Long botId = entry.getKey();
            if (requested.contains(botId)) {
                continue;
            }

            stopAndAwait(botId, entry.getValue());
        }
    }

    void startRequestedWhenSafe(
            Set<Long> requestedBotIds,
            BotRunScheduler scheduler
    ) {
        Set<Long> requested = normalize(requestedBotIds);

        for (Long botId : requested) {
            if (handles.containsKey(botId)) {
                continue;
            }

            if (scheduler.isWorking(botId)) {
                log.info(
                        "[SESSION PREVIEW] Bot {} preview requested while a normal job is still WORKING. Waiting for that job to finish before opening the visible read-only session.",
                        botId
                );
                continue;
            }

            start(botId);
        }
    }

    Set<Long> activeBotIds() {
        Set<Long> active = new HashSet<>();

        handles.forEach(
                (botId, handle) -> {
                    Future<?> future = handle.future;
                    if (future != null && !future.isDone()) {
                        active.add(botId);
                    }
                }
        );

        return Set.copyOf(active);
    }

    private void start(Long botId) {
        PreviewHandle handle = new PreviewHandle();

        if (handles.putIfAbsent(botId, handle) != null) {
            return;
        }

        try {
            handle.future = executor.submit(
                    () -> runPreview(botId, handle)
            );

            log.info(
                    "[SESSION PREVIEW] Starting dedicated visible read-only preview for bot {}.",
                    botId
            );
        } catch (RuntimeException exception) {
            handles.remove(botId, handle);
            throw exception;
        }
    }

    private void runPreview(
            Long botId,
            PreviewHandle handle
    ) {
        try {
            BotDetailsDto bot = botApiClient.getBot(botId);

            try (BrowserManager browserManager = new BrowserManager(false);
                 BotContext context =
                         BotContext.readOnlySessionClone(bot, browserManager)) {

                MarketplaceNavigator navigator =
                        new MarketplaceNavigator(context);

                navigator.goToHome();

                Page page = context.getPage();

                log.info(
                        "[SESSION PREVIEW] Visible preview READY for bot {}. storedSessionRestored={}, url={}. Normal scheduled jobs remain paused and the preview cannot overwrite the production session file.",
                        botId,
                        context.isStoredSessionRestored(),
                        page.url()
                );

                while (!handle.stopRequested.get()
                        && page != null
                        && !page.isClosed()) {
                    page.waitForTimeout(PREVIEW_POLL_MS);
                }

                if (page != null && page.isClosed()
                        && !handle.stopRequested.get()) {
                    log.info(
                            "[SESSION PREVIEW] Visible preview window for bot {} was closed manually. The bot remains scheduler-paused until 'Ukryj sesję' clears the preview request, preventing an unnoticed session-owner race.",
                            botId
                    );
                }
            }
        } catch (RuntimeException exception) {
            log.error(
                    "[SESSION PREVIEW] Could not open visible read-only preview for bot {}. The normal bot remains paused while the preview request is enabled; production session state was not modified. reason={}",
                    botId,
                    friendlyMessage(exception),
                    exception
            );
        } finally {
            handle.finished.set(true);
        }
    }

    private void stopAndAwait(
            Long botId,
            PreviewHandle handle
    ) {
        handle.stopRequested.set(true);

        Future<?> future = handle.future;

        if (future == null) {
            handles.remove(botId, handle);
            return;
        }

        try {
            future.get(
                    STOP_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
            );
            handles.remove(botId, handle);

            log.info(
                    "[SESSION PREVIEW] Visible preview for bot {} is fully closed. Normal scheduler ownership may resume.",
                    botId
            );
        } catch (TimeoutException exception) {
            /*
             * Fail safe: keep the handle registered. WorkerManager includes
             * active preview handles in scheduler pause ownership, so a normal
             * job cannot begin while this browser might still be alive.
             */
            log.error(
                    "[SESSION PREVIEW] Timed out waiting for bot {} preview to close. Keeping this bot scheduler-paused until the preview thread actually exits.",
                    botId
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn(
                    "[SESSION PREVIEW] Interrupted while closing preview for bot {}. Keeping scheduler ownership conservative.",
                    botId
            );
        } catch (Exception exception) {
            handles.remove(botId, handle);
            log.warn(
                    "[SESSION PREVIEW] Preview thread for bot {} finished with an error while closing. Runtime ownership is released because the thread is no longer active.",
                    botId,
                    exception
            );
        }
    }

    private Set<Long> normalize(Set<Long> botIds) {
        if (botIds == null || botIds.isEmpty()) {
            return Set.of();
        }

        Set<Long> normalized = new HashSet<>();

        botIds.stream()
                .filter(botId -> botId != null && botId > 0L)
                .forEach(normalized::add);

        return Set.copyOf(normalized);
    }

    @Override
    public void close() {
        Set<Long> botIds = Set.copyOf(handles.keySet());

        for (Long botId : botIds) {
            PreviewHandle handle = handles.get(botId);
            if (handle != null) {
                stopAndAwait(botId, handle);
            }
        }

        executor.shutdownNow();

        try {
            if (!executor.awaitTermination(
                    STOP_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
            )) {
                log.warn(
                        "[SESSION PREVIEW] Preview executor did not terminate within {} seconds.",
                        STOP_TIMEOUT_SECONDS
                );
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private String friendlyMessage(Throwable throwable) {
        if (throwable == null) {
            return "unknown error";
        }

        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }

        return message.lines()
                .findFirst()
                .orElse(message)
                .trim();
    }

    private static java.util.concurrent.ThreadFactory namedThreadFactory(
            String prefix
    ) {
        AtomicInteger sequence = new AtomicInteger(1);

        return runnable -> {
            Thread thread = new Thread(
                    runnable,
                    prefix + sequence.getAndIncrement()
            );
            thread.setDaemon(false);
            return thread;
        };
    }

    private static final class PreviewHandle {
        private final AtomicBoolean stopRequested =
                new AtomicBoolean(false);
        private final AtomicBoolean finished =
                new AtomicBoolean(false);
        private volatile Future<?> future;
    }
}
