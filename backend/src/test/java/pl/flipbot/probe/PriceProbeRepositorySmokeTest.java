package pl.flipbot.probe;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class PriceProbeRepositorySmokeTest {
    @Test
    void repositoryCompiles() {
        assertNotNull(PriceProbeRepository.class);
    }
}
