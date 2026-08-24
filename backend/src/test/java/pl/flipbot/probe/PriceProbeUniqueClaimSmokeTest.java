package pl.flipbot.probe;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class PriceProbeUniqueClaimSmokeTest {
    @Test
    void repositoryTypeExists() {
        assertNotNull(PriceProbeRepository.class);
    }
}
