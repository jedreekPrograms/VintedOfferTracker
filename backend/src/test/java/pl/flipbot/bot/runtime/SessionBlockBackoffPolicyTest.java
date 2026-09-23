package pl.flipbot.bot.runtime;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SessionBlockBackoffPolicyTest {

    private final SessionBlockBackoffPolicy policy = new SessionBlockBackoffPolicy();

    @Test
    void doublesRetryDelayForEachRepeatedSessionBlock() {
        assertEquals(Duration.ofMinutes(15), policy.delayForAttempt(1));
        assertEquals(Duration.ofMinutes(30), policy.delayForAttempt(2));
        assertEquals(Duration.ofHours(1), policy.delayForAttempt(3));
        assertEquals(Duration.ofHours(2), policy.delayForAttempt(4));
        assertEquals(Duration.ofHours(4), policy.delayForAttempt(5));
        assertEquals(Duration.ofHours(8), policy.delayForAttempt(6));
        assertEquals(Duration.ofHours(16), policy.delayForAttempt(7));
    }
}
