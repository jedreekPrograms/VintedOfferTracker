package pl.flipbot.bot.runtime.captcha;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CaptchaControlService {

    private static final int MAX_MESSAGE_LENGTH = 1_000;

    private final Map<Long, MutableCaptchaControlState> states =
            new ConcurrentHashMap<>();

    public CaptchaControlStateResponse getState(Long botId) {
        MutableCaptchaControlState state = states.get(botId);
        if (state == null) {
            return new CaptchaControlStateResponse(
                    botId,
                    CaptchaControlStatus.IDLE,
                    null,
                    null,
                    null
            );
        }

        synchronized (state) {
            return snapshot(botId, state);
        }
    }

    public CaptchaControlStateResponse prepare(Long botId) {
        MutableCaptchaControlState state = new MutableCaptchaControlState();
        state.status = CaptchaControlStatus.PREPARING;
        state.updatedAt = Instant.now();
        states.put(botId, state);
        return snapshot(botId, state);
    }

    public CaptchaControlStateResponse markReady(Long botId) {
        MutableCaptchaControlState state = stateFor(botId);
        synchronized (state) {
            state.status = CaptchaControlStatus.READY;
            state.updatedAt = Instant.now();
            state.holdHeartbeatAt = null;
            state.message = null;
            return snapshot(botId, state);
        }
    }

    public CaptchaControlStateResponse startHold(Long botId) {
        MutableCaptchaControlState state = stateFor(botId);
        synchronized (state) {
            if (state.status != CaptchaControlStatus.READY
                    && state.status != CaptchaControlStatus.HOLDING) {
                throw new IllegalStateException(
                        "CAPTCHA browser is not ready for remote hold control."
                );
            }

            Instant now = Instant.now();
            state.status = CaptchaControlStatus.HOLDING;
            state.updatedAt = now;
            state.holdHeartbeatAt = now;
            return snapshot(botId, state);
        }
    }

    public CaptchaControlStateResponse heartbeat(Long botId) {
        MutableCaptchaControlState state = stateFor(botId);
        synchronized (state) {
            if (state.status != CaptchaControlStatus.HOLDING) {
                throw new IllegalStateException(
                        "CAPTCHA hold is not active."
                );
            }

            Instant now = Instant.now();
            state.updatedAt = now;
            state.holdHeartbeatAt = now;
            return snapshot(botId, state);
        }
    }

    public CaptchaControlStateResponse endHold(Long botId) {
        MutableCaptchaControlState state = stateFor(botId);
        synchronized (state) {
            if (state.status == CaptchaControlStatus.HOLDING) {
                state.status = CaptchaControlStatus.READY;
                state.updatedAt = Instant.now();
                state.holdHeartbeatAt = null;
            }
            return snapshot(botId, state);
        }
    }

    public CaptchaControlStateResponse markCompleted(Long botId) {
        MutableCaptchaControlState state = stateFor(botId);
        synchronized (state) {
            state.status = CaptchaControlStatus.COMPLETED;
            state.updatedAt = Instant.now();
            state.holdHeartbeatAt = null;
            state.message = null;
            return snapshot(botId, state);
        }
    }

    public CaptchaControlStateResponse markFailed(
            Long botId,
            String message
    ) {
        MutableCaptchaControlState state = stateFor(botId);
        synchronized (state) {
            state.status = CaptchaControlStatus.FAILED;
            state.updatedAt = Instant.now();
            state.holdHeartbeatAt = null;
            state.message = normalizeMessage(message);
            return snapshot(botId, state);
        }
    }

    public void reset(Long botId) {
        states.remove(botId);
    }

    private MutableCaptchaControlState stateFor(Long botId) {
        return states.computeIfAbsent(
                botId,
                ignored -> {
                    MutableCaptchaControlState state =
                            new MutableCaptchaControlState();
                    state.status = CaptchaControlStatus.PREPARING;
                    state.updatedAt = Instant.now();
                    return state;
                }
        );
    }

    private CaptchaControlStateResponse snapshot(
            Long botId,
            MutableCaptchaControlState state
    ) {
        return new CaptchaControlStateResponse(
                botId,
                state.status,
                state.updatedAt,
                state.holdHeartbeatAt,
                state.message
        );
    }

    private String normalizeMessage(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }

        String normalized = message.trim();
        if (normalized.length() <= MAX_MESSAGE_LENGTH) {
            return normalized;
        }

        return normalized.substring(0, MAX_MESSAGE_LENGTH);
    }

    private static final class MutableCaptchaControlState {
        private CaptchaControlStatus status;
        private Instant updatedAt;
        private Instant holdHeartbeatAt;
        private String message;
    }
}
