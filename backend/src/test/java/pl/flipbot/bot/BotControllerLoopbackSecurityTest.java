package pl.flipbot.bot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BotControllerLoopbackSecurityTest {

    @Test
    void acceptsIpv4AndIpv6LoopbackOnly() {
        assertTrue(BotController.isLoopbackAddress("127.0.0.1"));
        assertTrue(BotController.isLoopbackAddress("::1"));

        assertFalse(BotController.isLoopbackAddress("192.168.1.50"));
        assertFalse(BotController.isLoopbackAddress("10.0.0.12"));
        assertFalse(BotController.isLoopbackAddress(""));
        assertFalse(BotController.isLoopbackAddress(null));
    }
}
