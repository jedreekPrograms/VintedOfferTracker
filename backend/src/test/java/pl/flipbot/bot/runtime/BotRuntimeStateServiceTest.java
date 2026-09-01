package pl.flipbot.bot.runtime;

import org.junit.jupiter.api.Test;
import pl.flipbot.bot.BotRepository;
import pl.flipbot.bot.runtime.dto.BotRuntimeEventRequest;
import pl.flipbot.bot.runtime.dto.BotRuntimeStateResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    private BotRuntimeEventRequest sessionBlockedRequest() {
        BotRuntimeEventRequest request = new BotRuntimeEventRequest();
        request.setEventType(RuntimeEventType.SESSION_BLOCKED);
        request.setDurationMs(1_000L);
        request.setErrorMessage("Vinted session block detected");
        return request;
    }
}
