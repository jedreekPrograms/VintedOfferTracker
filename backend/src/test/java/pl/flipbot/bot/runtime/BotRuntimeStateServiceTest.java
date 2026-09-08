package pl.flipbot.bot.runtime;

import org.junit.jupiter.api.Test;
import pl.flipbot.bot.Bot;
import pl.flipbot.bot.BotRepository;
import pl.flipbot.bot.BotStatus;
import pl.flipbot.bot.runtime.dto.BotRuntimeEventRequest;
import pl.flipbot.bot.runtime.dto.BotRuntimeStateResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BotRuntimeStateServiceTest {

    @Test
    void sessionBlockReclassifiesGenericFailuresAndKeepsOriginalEpisodeStart() {
        BotRepository botRepository = mock(BotRepository.class);
        BotRuntimeStateRepository runtimeStateRepository =
                mock(BotRuntimeStateRepository.class);

        BotRuntimeState state = new BotRuntimeState();
        state.setBotId(3L);
        state.setRuntimeStatus(BotRuntimeStatus.ERROR);
        state.setConsecutiveFailures(358);
        state.setLastError("IllegalStateException: stale login failure");
        state.setSessionBlockCount(0);
        state.setUpdatedAt(Instant.now());

        when(runtimeStateRepository.findById(3L))
                .thenReturn(Optional.of(state));
        when(runtimeStateRepository.save(any(BotRuntimeState.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BotRuntimeStateService service = new BotRuntimeStateService(
                botRepository,
                runtimeStateRepository
        );

        BotRuntimeStateResponse first = service.applyEvent(
                3L,
                sessionBlockedRequest()
        );

        assertEquals(BotRuntimeStatus.COOLDOWN, first.getRuntimeStatus());
        assertEquals(0, first.getConsecutiveFailures());
        assertEquals(1, first.getSessionBlockCount());
        assertNotNull(first.getSessionBlockedSince());
        assertNotNull(first.getNextRunAt());

        long firstDelayMinutes = Duration.between(
                first.getLastRunFinishedAt(),
                first.getNextRunAt()
        ).toMinutes();
        assertEquals(15L, firstDelayMinutes);

        Instant originalBlockedSince = first.getSessionBlockedSince();

        BotRuntimeStateResponse second = service.applyEvent(
                3L,
                sessionBlockedRequest()
        );

        assertEquals(0, second.getConsecutiveFailures());
        assertEquals(2, second.getSessionBlockCount());
        assertEquals(originalBlockedSince, second.getSessionBlockedSince());

        long secondDelayMinutes = Duration.between(
                second.getLastRunFinishedAt(),
                second.getNextRunAt()
        ).toMinutes();
        assertEquals(30L, secondDelayMinutes);
        assertTrue(second.getLastError().contains("session block"));
    }

    @Test
    void captchaWaitsForExplicitRecoveryRequestAndQueuedEventsCannotWakeIt() {
        BotRepository botRepository = mock(BotRepository.class);
        BotRuntimeStateRepository runtimeStateRepository =
                mock(BotRuntimeStateRepository.class);

        Bot bot = Bot.builder()
                .id(7L)
                .status(BotStatus.RUNNING)
                .build();

        BotRuntimeState state = new BotRuntimeState();
        state.setBotId(7L);
        state.setBot(bot);
        state.setRuntimeStatus(BotRuntimeStatus.WORKING);
        state.setConsecutiveFailures(2);
        state.setSessionBlockCount(0);
        state.setUpdatedAt(Instant.now());

        when(botRepository.findById(7L)).thenReturn(Optional.of(bot));
        when(runtimeStateRepository.findById(7L))
                .thenReturn(Optional.of(state));
        when(runtimeStateRepository.save(any(BotRuntimeState.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BotRuntimeStateService service = new BotRuntimeStateService(
                botRepository,
                runtimeStateRepository
        );

        BotRuntimeEventRequest requiredRequest = new BotRuntimeEventRequest();
        requiredRequest.setEventType(RuntimeEventType.CAPTCHA_REQUIRED);
        requiredRequest.setChallengeUrl(" https://www.vinted.pl/captcha?x=1 ");
        requiredRequest.setErrorMessage("Manual CAPTCHA completion required");

        BotRuntimeStateResponse required = service.applyEvent(
                7L,
                requiredRequest
        );

        assertEquals(BotRuntimeStatus.CAPTCHA_REQUIRED, required.getRuntimeStatus());
        assertNotNull(required.getCaptchaRequiredSince());
        assertNull(required.getCaptchaRecoveryRequestedAt());
        assertNull(required.getNextRunAt());
        assertEquals("https://www.vinted.pl/captcha?x=1", required.getCaptchaChallengeUrl());
        assertEquals(0, required.getConsecutiveFailures());

        Instant requiredSince = required.getCaptchaRequiredSince();

        BotRuntimeEventRequest queuedRequest = new BotRuntimeEventRequest();
        queuedRequest.setEventType(RuntimeEventType.QUEUED);
        queuedRequest.setNextRunAtEpochMs(System.currentTimeMillis());

        BotRuntimeStateResponse stillPaused = service.applyEvent(
                7L,
                queuedRequest
        );

        assertEquals(BotRuntimeStatus.CAPTCHA_REQUIRED, stillPaused.getRuntimeStatus());
        assertEquals(requiredSince, stillPaused.getCaptchaRequiredSince());
        assertNull(stillPaused.getNextRunAt());

        BotRuntimeStateResponse requested = service.requestCaptchaRecovery(7L);

        assertNotNull(requested.getCaptchaRecoveryRequestedAt());
        assertEquals(BotRuntimeStatus.CAPTCHA_REQUIRED, requested.getRuntimeStatus());

        Instant firstRequest = requested.getCaptchaRecoveryRequestedAt();
        BotRuntimeStateResponse duplicateRequest = service.requestCaptchaRecovery(7L);
        assertEquals(firstRequest, duplicateRequest.getCaptchaRecoveryRequestedAt());
    }

    @Test
    void successfulCaptchaRecoveryClearsPersistentPause() {
        BotRepository botRepository = mock(BotRepository.class);
        BotRuntimeStateRepository runtimeStateRepository =
                mock(BotRuntimeStateRepository.class);

        BotRuntimeState state = new BotRuntimeState();
        state.setBotId(9L);
        state.setRuntimeStatus(BotRuntimeStatus.CAPTCHA_REQUIRED);
        state.setConsecutiveFailures(0);
        state.setSessionBlockCount(0);
        state.setCaptchaRequiredSince(Instant.now().minusSeconds(60));
        state.setCaptchaRecoveryRequestedAt(Instant.now());
        state.setCaptchaChallengeUrl("https://www.vinted.pl/captcha");
        state.setUpdatedAt(Instant.now());

        when(runtimeStateRepository.findById(9L))
                .thenReturn(Optional.of(state));
        when(runtimeStateRepository.save(any(BotRuntimeState.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BotRuntimeStateService service = new BotRuntimeStateService(
                botRepository,
                runtimeStateRepository
        );

        BotRuntimeEventRequest startedRequest = new BotRuntimeEventRequest();
        startedRequest.setEventType(RuntimeEventType.CAPTCHA_RECOVERY_STARTED);
        startedRequest.setWorkerSlot(3);

        BotRuntimeStateResponse started = service.applyEvent(9L, startedRequest);
        assertEquals(BotRuntimeStatus.WORKING, started.getRuntimeStatus());
        assertNotNull(started.getCaptchaRequiredSince());
        assertEquals(3, started.getWorkerSlot());

        BotRuntimeEventRequest succeededRequest = new BotRuntimeEventRequest();
        succeededRequest.setEventType(RuntimeEventType.CAPTCHA_RECOVERY_SUCCEEDED);
        succeededRequest.setDurationMs(4_000L);

        BotRuntimeStateResponse succeeded = service.applyEvent(
                9L,
                succeededRequest
        );

        assertEquals(BotRuntimeStatus.IDLE, succeeded.getRuntimeStatus());
        assertNull(succeeded.getCaptchaRequiredSince());
        assertNull(succeeded.getCaptchaRecoveryRequestedAt());
        assertNull(succeeded.getCaptchaChallengeUrl());
        assertNull(succeeded.getLastError());
    }

    private BotRuntimeEventRequest sessionBlockedRequest() {
        BotRuntimeEventRequest request = new BotRuntimeEventRequest();
        request.setEventType(RuntimeEventType.SESSION_BLOCKED);
        request.setDurationMs(1_000L);
        request.setErrorMessage("Vinted session block detected");
        return request;
    }
}
