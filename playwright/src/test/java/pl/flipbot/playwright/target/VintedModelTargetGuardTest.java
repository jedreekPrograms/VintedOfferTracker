package pl.flipbot.playwright.target;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VintedModelTargetGuardTest {

    private final VintedModelTargetGuard guard =
            new VintedModelTargetGuard();

    @Test
    void rejectsFlip4WhenBotTargetsS25() {
        assertTrue(
                guard.findConclusiveMismatch(
                        "Galaxy S25",
                        "Samsung Galaxy Z Flip 4"
                ).isPresent()
        );
    }

    @Test
    void rejectsDifferentSSeriesModel() {
        assertTrue(
                guard.findConclusiveMismatch(
                        "Galaxy S25",
                        "Samsung Galaxy S24 256 GB"
                ).isPresent()
        );
    }

    @Test
    void rejectsUnexpectedVariant() {
        assertTrue(
                guard.findConclusiveMismatch(
                        "Galaxy S25",
                        "Samsung Galaxy S25 Ultra"
                ).isPresent()
        );
    }

    @Test
    void acceptsMatchingS25Title() {
        assertFalse(
                guard.findConclusiveMismatch(
                        "Galaxy S25",
                        "Telefon Samsung S25 128GB"
                ).isPresent()
        );
    }

    @Test
    void doesNotRejectAmbiguousGenericTitle() {
        assertFalse(
                guard.findConclusiveMismatch(
                        "Galaxy S25",
                        "Samsung telefon 128 GB czarny"
                ).isPresent()
        );
    }

    @Test
    void ignoresSamsungTechnicalProductCodeAsModelEvidence() {
        assertFalse(
                guard.findConclusiveMismatch(
                        "Galaxy S25",
                        "Samsung SM-S931 12/128 GB"
                ).isPresent()
        );
    }
}
