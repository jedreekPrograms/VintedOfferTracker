package pl.flipbot.playwright.verification;

import com.microsoft.playwright.Frame;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.BoundingBox;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.runtime.CaptchaControlClient;
import pl.flipbot.playwright.api.runtime.CaptchaControlStateResponse;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Converts a live human "hold" gesture from FlipBot Mobile into a horizontal
 * pointer drag in the already-open headed browser.
 *
 * <p>The controller never starts or continues movement by itself: a recent
 * HOLDING heartbeat from the mobile UI is required for every movement tick.
 * Losing the phone connection releases the mouse immediately.</p>
 */
@Slf4j
final class MobileCaptchaDragController {

    static final long HEARTBEAT_MAX_AGE_MS = 650L;
    static final double MOVE_STEP_PX = 10.0;
    static final double MAX_DRAG_DISTANCE_PX = 420.0;

    private static final List<String> SLIDER_HANDLE_SELECTORS = List.of(
            "[role='slider']",
            "input[type='range']",
            "[aria-valuemin][aria-valuemax]",
            "[data-testid*='slider' i]",
            "[class*='slider' i] [class*='handle' i]",
            "[class*='slider' i] [class*='thumb' i]",
            "[class*='slider' i] [draggable='true']",
            "[class*='slider' i] button"
    );

    private final Page page;
    private final Long botId;
    private final CaptchaControlClient client;

    private boolean dragging;
    private double currentX;
    private double currentY;
    private double dragStartX;
    private long nextMissingHandleLogAt;

    MobileCaptchaDragController(
            Page page,
            Long botId,
            CaptchaControlClient client
    ) {
        this.page = page;
        this.botId = botId;
        this.client = client;
    }

    void markReady() {
        try {
            client.markReady(botId);
            log.info(
                    "[CAPTCHA MOBILE] Bot {} headed challenge is ready for human hold control from the mobile app.",
                    botId
            );
        } catch (RuntimeException exception) {
            log.warn(
                    "[CAPTCHA MOBILE] Could not publish READY state for bot {}. Local manual completion remains available. reason={}",
                    botId,
                    friendlyMessage(exception)
            );
        }
    }

    void tick() {
        CaptchaControlStateResponse state;
        try {
            state = client.getState(botId);
        } catch (RuntimeException exception) {
            releaseMouseQuietly();
            return;
        }

        if (!state.holding() || !hasFreshHeartbeat(state.holdHeartbeatAt())) {
            releaseMouseQuietly();
            return;
        }

        if (!dragging && !beginDrag()) {
            return;
        }

        double nextX = Math.min(
                currentX + MOVE_STEP_PX,
                dragStartX + MAX_DRAG_DISTANCE_PX
        );

        if (nextX <= currentX) {
            return;
        }

        try {
            page.mouse().move(nextX, currentY);
            currentX = nextX;
        } catch (PlaywrightException exception) {
            releaseMouseQuietly();
            log.debug(
                    "[CAPTCHA MOBILE] Pointer movement failed because the challenge changed."
            );
        }
    }

    void complete() {
        releaseMouseQuietly();
        try {
            client.markCompleted(botId);
        } catch (RuntimeException exception) {
            log.debug(
                    "[CAPTCHA MOBILE] Could not publish COMPLETED state for bot {}: {}",
                    botId,
                    friendlyMessage(exception)
            );
        }
    }

    void fail() {
        releaseMouseQuietly();
        try {
            client.markFailed(botId);
        } catch (RuntimeException exception) {
            log.debug(
                    "[CAPTCHA MOBILE] Could not publish FAILED state for bot {}: {}",
                    botId,
                    friendlyMessage(exception)
            );
        }
    }

    private boolean beginDrag() {
        BoundingBox box = findSliderHandle();
        if (box == null) {
            long now = System.currentTimeMillis();
            if (now >= nextMissingHandleLogAt) {
                log.warn(
                        "[CAPTCHA MOBILE] Bot {} received a live HOLD gesture, but no visible slider handle could be resolved yet. The browser remains open and local manual interaction is still possible.",
                        botId
                );
                nextMissingHandleLogAt = now + 3_000L;
            }
            return false;
        }

        currentX = box.x + box.width / 2.0;
        currentY = box.y + box.height / 2.0;
        dragStartX = currentX;

        try {
            page.mouse().move(currentX, currentY);
            page.mouse().down();
            dragging = true;
            log.info(
                    "[CAPTCHA MOBILE] Bot {} started a human-controlled drag from the mobile hold gesture.",
                    botId
            );
            return true;
        } catch (PlaywrightException exception) {
            dragging = false;
            return false;
        }
    }

    private BoundingBox findSliderHandle() {
        for (Frame frame : page.frames()) {
            for (String selector : SLIDER_HANDLE_SELECTORS) {
                try {
                    Locator candidates = frame.locator(selector);
                    int count = Math.min(candidates.count(), 12);
                    for (int index = 0; index < count; index++) {
                        Locator candidate = candidates.nth(index);
                        if (!candidate.isVisible()) {
                            continue;
                        }

                        BoundingBox box = candidate.boundingBox();
                        if (box == null
                                || box.width < 12
                                || box.height < 12
                                || box.width > 180
                                || box.height > 180) {
                            continue;
                        }

                        return box;
                    }
                } catch (PlaywrightException ignored) {
                    // The challenge frequently re-renders while selectors are inspected.
                }
            }
        }

        return null;
    }

    private boolean hasFreshHeartbeat(String heartbeatAt) {
        if (heartbeatAt == null || heartbeatAt.isBlank()) {
            return false;
        }

        try {
            Instant heartbeat = Instant.parse(heartbeatAt);
            long ageMs = Duration.between(heartbeat, Instant.now()).toMillis();
            return ageMs >= 0L && ageMs <= HEARTBEAT_MAX_AGE_MS;
        } catch (DateTimeParseException exception) {
            return false;
        }
    }

    private void releaseMouseQuietly() {
        if (!dragging) {
            return;
        }

        try {
            page.mouse().up();
        } catch (PlaywrightException ignored) {
            // Page/challenge may have disappeared exactly as the user released.
        } finally {
            dragging = false;
        }
    }

    private String friendlyMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }
}
