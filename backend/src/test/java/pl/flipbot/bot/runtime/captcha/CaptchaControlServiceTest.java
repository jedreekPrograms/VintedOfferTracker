package pl.flipbot.bot.runtime.captcha;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CaptchaControlServiceTest {

    @Test
    void holdRequiresReadyBrowserAndHeartbeatIsTransient() {
        CaptchaControlService service = new CaptchaControlService();

        CaptchaControlStateResponse preparing = service.prepare(3L);
        assertEquals(CaptchaControlStatus.PREPARING, preparing.status());
        assertThrows(IllegalStateException.class, () -> service.startHold(3L));

        CaptchaControlStateResponse ready = service.markReady(3L);
        assertEquals(CaptchaControlStatus.READY, ready.status());
        assertNull(ready.holdHeartbeatAt());

        CaptchaControlStateResponse holding = service.startHold(3L);
        assertEquals(CaptchaControlStatus.HOLDING, holding.status());
        assertNotNull(holding.holdHeartbeatAt());

        CaptchaControlStateResponse heartbeat = service.heartbeat(3L);
        assertEquals(CaptchaControlStatus.HOLDING, heartbeat.status());
        assertNotNull(heartbeat.holdHeartbeatAt());

        CaptchaControlStateResponse released = service.endHold(3L);
        assertEquals(CaptchaControlStatus.READY, released.status());
        assertNull(released.holdHeartbeatAt());
    }

    @Test
    void completedAndFailedStatesAreResetByNextPrepare() {
        CaptchaControlService service = new CaptchaControlService();

        service.prepare(4L);
        service.markReady(4L);
        assertEquals(
                CaptchaControlStatus.COMPLETED,
                service.markCompleted(4L).status()
        );

        assertEquals(
                CaptchaControlStatus.PREPARING,
                service.prepare(4L).status()
        );

        assertEquals(
                CaptchaControlStatus.FAILED,
                service.markFailed(4L, "browser closed").status()
        );
        assertEquals("browser closed", service.getState(4L).message());
    }
}
