package pl.flipbot.bot.runtime;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.flipbot.bot.Bot;
import pl.flipbot.bot.BotRepository;
import pl.flipbot.bot.runtime.dto.BotRuntimeEventRequest;
import pl.flipbot.bot.runtime.dto.BotRuntimeStateResponse;
import pl.flipbot.exception.BotNotFoundException;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class BotRuntimeStateService {

    private static final int MAX_ERROR_LENGTH = 4_000;

    private final BotRepository botRepository;
    private final BotRuntimeStateRepository runtimeStateRepository;

    private final SessionBlockBackoffPolicy sessionBlockBackoffPolicy =
            new SessionBlockBackoffPolicy();

    @Transactional
    public BotRuntimeStateResponse getRuntimeState(Long botId) {
        return toResponse(getOrCreateState(botId));
    }

    @Transactional
    public BotRuntimeStateResponse applyEvent(
            Long botId,
            BotRuntimeEventRequest request
    ) {
        if (request == null || request.getEventType() == null) {
            throw new IllegalArgumentException("Runtime event type is required.");
        }

        BotRuntimeState state = getOrCreateState(botId);
        Instant now = Instant.now();

        switch (request.getEventType()) {
            case QUEUED -> applyQueued(state, request);
            case RUN_STARTED -> applyRunStarted(state, request, now);
            case RUN_SUCCEEDED -> applyRunSucceeded(state, request, now);
            case RUN_FAILED -> applyRunFailed(state, request, now);
            case RATE_LIMITED -> applyRateLimited(state, request, now);
            case SESSION_BLOCKED -> applySessionBlocked(state, request, now);
            case IDLE -> applyIdle(state);
        }

        state.setUpdatedAt(now);

        return toResponse(runtimeStateRepository.save(state));
    }

    private BotRuntimeState getOrCreateState(Long botId) {
        return runtimeStateRepository.findById(botId)
                .orElseGet(() -> createInitialState(botId));
    }

    private BotRuntimeState createInitialState(Long botId) {
        Bot bot = botRepository.findById(botId)
                .orElseThrow(() -> new BotNotFoundException(botId));

        BotRuntimeState state = new BotRuntimeState();
        state.setBot(bot);
        state.setRuntimeStatus(BotRuntimeStatus.IDLE);
        state.setConsecutiveFailures(0);
        state.setSessionBlockCount(0);
        state.setUpdatedAt(Instant.now());

        return runtimeStateRepository.save(state);
    }

    private void applyQueued(
            BotRuntimeState state,
            BotRuntimeEventRequest request
    ) {
        state.setRuntimeStatus(BotRuntimeStatus.QUEUED);
        state.setNextRunAt(toInstant(request.getNextRunAtEpochMs()));
        state.setWorkerSlot(null);
    }

    private void applyRunStarted(
            BotRuntimeState state,
            BotRuntimeEventRequest request,
            Instant now
    ) {
        state.setRuntimeStatus(BotRuntimeStatus.WORKING);
        state.setLastRunStartedAt(now);
        state.setNextRunAt(null);
        state.setWorkerSlot(request.getWorkerSlot());

        /*
         * Do not clear sessionBlockedSince here. A retry is only a probe: if
         * Vinted still shows the block page, the UI must keep counting from
         * the first detection. The episode is cleared only by a successful
         * full scheduled job (or when the bot is explicitly idled/stopped).
         */
    }

    private void applyRunSucceeded(
            BotRuntimeState state,
            BotRuntimeEventRequest request,
            Instant now
    ) {
        state.setRuntimeStatus(BotRuntimeStatus.IDLE);
        state.setLastRunFinishedAt(now);
        state.setLastRunDurationMs(safeDuration(request.getDurationMs()));
        state.setNextRunAt(null);
        state.setConsecutiveFailures(0);
        state.setLastError(null);
        state.setWorkerSlot(null);
        clearSessionBlockEpisode(state);
    }

    private void applyRunFailed(
            BotRuntimeState state,
            BotRuntimeEventRequest request,
            Instant now
    ) {
        state.setRuntimeStatus(BotRuntimeStatus.ERROR);
        state.setLastRunFinishedAt(now);
        state.setLastRunDurationMs(safeDuration(request.getDurationMs()));
        state.setNextRunAt(toInstant(request.getNextRunAtEpochMs()));
        state.setConsecutiveFailures(state.getConsecutiveFailures() + 1);
        state.setLastError(normalizeError(request.getErrorMessage()));
        state.setWorkerSlot(null);
    }

    private void applyRateLimited(
            BotRuntimeState state,
            BotRuntimeEventRequest request,
            Instant now
    ) {
        state.setRuntimeStatus(BotRuntimeStatus.COOLDOWN);
        state.setLastRunFinishedAt(now);
        state.setLastRunDurationMs(safeDuration(request.getDurationMs()));
        state.setNextRunAt(toInstant(request.getNextRunAtEpochMs()));
        state.setLastError(normalizeError(request.getErrorMessage()));
        state.setWorkerSlot(null);
    }

    private void applySessionBlocked(
            BotRuntimeState state,
            BotRuntimeEventRequest request,
            Instant now
    ) {
        if (state.getSessionBlockedSince() == null) {
            state.setSessionBlockedSince(now);
            state.setSessionBlockCount(0);
        }

        int attemptNumber = Math.max(0, state.getSessionBlockCount()) + 1;
        Duration retryDelay = sessionBlockBackoffPolicy.delayForAttempt(attemptNumber);

        state.setRuntimeStatus(BotRuntimeStatus.COOLDOWN);
        state.setLastRunFinishedAt(now);
        state.setLastRunDurationMs(safeDuration(request.getDurationMs()));
        state.setNextRunAt(now.plus(retryDelay));
        state.setSessionBlockCount(attemptNumber);
        state.setLastError(normalizeError(request.getErrorMessage()));
        state.setWorkerSlot(null);
    }

    private void applyIdle(BotRuntimeState state) {
        state.setRuntimeStatus(BotRuntimeStatus.IDLE);
        state.setNextRunAt(null);
        state.setWorkerSlot(null);
        state.setLastError(null);
        clearSessionBlockEpisode(state);
    }

    private void clearSessionBlockEpisode(BotRuntimeState state) {
        state.setSessionBlockedSince(null);
        state.setSessionBlockCount(0);
    }

    private Long safeDuration(Long durationMs) {
        if (durationMs == null) {
            return null;
        }

        return Math.max(0L, durationMs);
    }

    private Instant toInstant(Long epochMs) {
        if (epochMs == null) {
            return null;
        }

        return Instant.ofEpochMilli(epochMs);
    }

    private String normalizeError(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }

        String normalized = message.trim();

        if (normalized.length() <= MAX_ERROR_LENGTH) {
            return normalized;
        }

        return normalized.substring(0, MAX_ERROR_LENGTH);
    }

    private BotRuntimeStateResponse toResponse(BotRuntimeState state) {
        return BotRuntimeStateResponse.builder()
                .botId(state.getBotId())
                .runtimeStatus(state.getRuntimeStatus())
                .lastRunStartedAt(state.getLastRunStartedAt())
                .lastRunFinishedAt(state.getLastRunFinishedAt())
                .nextRunAt(state.getNextRunAt())
                .lastRunDurationMs(state.getLastRunDurationMs())
                .consecutiveFailures(state.getConsecutiveFailures())
                .lastError(state.getLastError())
                .workerSlot(state.getWorkerSlot())
                .sessionBlockedSince(state.getSessionBlockedSince())
                .sessionBlockCount(state.getSessionBlockCount())
                .updatedAt(state.getUpdatedAt())
                .build();
    }
}
