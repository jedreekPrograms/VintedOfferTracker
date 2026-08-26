package pl.flipbot.bot;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BotControllerTransactionBoundaryTest {

    @Test
    void botReadEndpointsKeepPersistenceContextOpenForLazyConfigurationMapping() throws Exception {
        assertReadOnlyTransaction("getAllBots");
        assertReadOnlyTransaction("getBot", Long.class);
        assertReadOnlyTransaction("getEditCapabilities", Long.class);
        assertReadOnlyTransaction("getPlaywrightBot", Long.class);
    }

    private void assertReadOnlyTransaction(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = BotController.class.getMethod(methodName, parameterTypes);
        Transactional transactional = method.getAnnotation(Transactional.class);

        assertNotNull(
                transactional,
                () -> methodName + " must keep a transaction open while BotMapper reads lazy associations"
        );
        assertTrue(
                transactional.readOnly(),
                () -> methodName + " should use a read-only transaction"
        );
    }
}
