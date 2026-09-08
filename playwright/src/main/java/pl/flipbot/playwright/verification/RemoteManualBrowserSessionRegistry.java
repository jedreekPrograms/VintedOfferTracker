package pl.flipbot.playwright.verification;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.ScreenshotType;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe bridge between HTTP handlers and the Playwright thread that owns
 * the headed recovery page. HTTP threads only enqueue user input/read the most
 * recent frame. All Page/Mouse calls are performed by the owning worker thread.
 */
public final class RemoteManualBrowserSessionRegistry {

    private static final long FRAME_INTERVAL_MS = 350L;
    private static final RemoteManualBrowserSessionRegistry INSTANCE =
            new RemoteManualBrowserSessionRegistry();

    private final ConcurrentLinkedQueue<PointerCommand> pointerCommands =
            new ConcurrentLinkedQueue<>();
    private final AtomicReference<FrameSnapshot> latestFrame =
            new AtomicReference<>();
    private final AtomicLong frameVersion = new AtomicLong();

    private volatile Long activeBotId;
    private volatile long lastFrameCapturedAt;

    private RemoteManualBrowserSessionRegistry() {
    }

    public static RemoteManualBrowserSessionRegistry getInstance() {
        return INSTANCE;
    }

    public synchronized void open(Long botId) {
        Objects.requireNonNull(botId, "Bot ID cannot be null");
        activeBotId = botId;
        pointerCommands.clear();
        latestFrame.set(null);
        frameVersion.set(0L);
        lastFrameCapturedAt = 0L;
    }

    public synchronized void close(Long botId) {
        if (!Objects.equals(activeBotId, botId)) {
            return;
        }
        activeBotId = null;
        pointerCommands.clear();
        latestFrame.set(null);
        lastFrameCapturedAt = 0L;
    }

    public Long stateForActiveBot() {
        return activeBotId;
    }

    public RemoteState state(Long requestedBotId) {
        Long currentBotId = activeBotId;
        FrameSnapshot frame = latestFrame.get();
        boolean active = currentBotId != null && Objects.equals(currentBotId, requestedBotId);

        return new RemoteState(
                active,
                active ? currentBotId : null,
                active && frame != null,
                active && frame != null ? frame.version() : 0L,
                active && frame != null ? frame.width() : 0,
                active && frame != null ? frame.height() : 0
        );
    }

    public FrameSnapshot frame(Long requestedBotId) {
        if (!Objects.equals(activeBotId, requestedBotId)) {
            return null;
        }
        return latestFrame.get();
    }

    public void enqueue(Long botId, PointerCommand command) {
        Objects.requireNonNull(command, "Pointer command cannot be null");
        if (!Objects.equals(activeBotId, botId)) {
            throw new IllegalStateException("No active manual browser session for bot " + botId);
        }
        command.validate();
        pointerCommands.add(command);
    }

    /** Must only be invoked by the thread that owns {@code page}. */
    public void process(Page page, Long botId) {
        if (!Objects.equals(activeBotId, botId) || page == null || page.isClosed()) {
            return;
        }

        int[] viewport = viewport(page);
        drainPointerCommands(page, botId, viewport[0], viewport[1]);

        long now = System.currentTimeMillis();
        if (now - lastFrameCapturedAt < FRAME_INTERVAL_MS) {
            return;
        }

        byte[] jpeg = page.screenshot(
                new Page.ScreenshotOptions()
                        .setType(ScreenshotType.JPEG)
                        .setQuality(58)
                        .setFullPage(false)
        );
        long version = frameVersion.incrementAndGet();
        latestFrame.set(new FrameSnapshot(jpeg, viewport[0], viewport[1], version));
        lastFrameCapturedAt = now;
    }

    /** Must only be invoked by the thread that owns {@code page}. */
    public void releasePointer(Page page, Long botId) {
        if (!Objects.equals(activeBotId, botId) || page == null || page.isClosed()) {
            return;
        }
        try {
            page.mouse().up();
        } catch (RuntimeException ignored) {
            // Best-effort fail-safe when the page is already navigating/closing.
        }
    }

    private void drainPointerCommands(Page page, Long botId, int width, int height) {
        PointerCommand command;
        while ((command = pointerCommands.poll()) != null) {
            if (!Objects.equals(activeBotId, botId)) {
                return;
            }

            double x = command.x() * Math.max(1, width - 1);
            double y = command.y() * Math.max(1, height - 1);
            page.mouse().move(x, y);

            switch (command.type()) {
                case DOWN -> page.mouse().down();
                case MOVE -> {
                    // The real user's pointer position is already applied above.
                }
                case UP -> page.mouse().up();
            }
        }
    }

    private int[] viewport(Page page) {
        Object result = page.evaluate(
                "() => ({ width: Math.max(1, window.innerWidth), height: Math.max(1, window.innerHeight) })"
        );
        if (result instanceof Map<?, ?> map) {
            Object width = map.get("width");
            Object height = map.get("height");
            if (width instanceof Number widthNumber && height instanceof Number heightNumber) {
                return new int[]{
                        Math.max(1, widthNumber.intValue()),
                        Math.max(1, heightNumber.intValue())
                };
            }
        }
        return new int[]{1280, 720};
    }

    public enum PointerType {
        DOWN,
        MOVE,
        UP
    }

    public record PointerCommand(PointerType type, double x, double y) {
        public void validate() {
            Objects.requireNonNull(type, "Pointer type cannot be null");
            if (!Double.isFinite(x) || !Double.isFinite(y)
                    || x < 0.0 || x > 1.0 || y < 0.0 || y > 1.0) {
                throw new IllegalArgumentException("Pointer coordinates must be normalized to [0, 1]");
            }
        }
    }

    public record RemoteState(
            boolean active,
            Long botId,
            boolean screenshotAvailable,
            long frameVersion,
            int viewportWidth,
            int viewportHeight
    ) {
    }

    public record FrameSnapshot(byte[] jpeg, int width, int height, long version) {
    }
}
